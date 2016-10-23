package mba210;
// CSE 431 P2 Maxwell Anselm

import agent.Action;
import agent.Agent;
import agent.Percept;

import pacworld.Direction;
import pacworld.Dropoff;
import pacworld.Idle;
import pacworld.Location;
import pacworld.Move;
import pacworld.PacPercept;
import pacworld.Pickup;
import pacworld.Say;
import pacworld.VisibleAgent;
import pacworld.VisiblePackage;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class PacAgent extends Agent
{
	// how many new things we need to know before we send a message
	final int discovery_share_thresh = 10;
	// copied from PacPercept for convenience
	final int vis_radius;

	// String id
	Coord pos;
	Coord goal;
	int holding;

	World world;
	Set<Coord> discoveries;
	Set<Coord> shared_discoveries;
	Set<Coord> dropoffs;
	Set<Coord> dropped_packages;
	Coord dropped_package;
	int possible_package;
	VisiblePackage held_package;
	boolean bumped;
	boolean delivered;
	Map<String, PacAgent> agents;

	private PacAgent(String id)
	{
		this.id = id;
		pos = null;
		goal = null;
		holding = -1;

		dropoffs = new HashSet<Coord>();
		dropped_package = null;
		vis_radius = PacPercept.VIS_RADIUS;
		discoveries = new HashSet<Coord>();
		shared_discoveries = new HashSet<Coord>();
		possible_package = -1;
		agents = new HashMap<String, PacAgent>();
		delivered = false;
		dropped_packages = new HashSet<Coord>();
		// XXX other initialization is done after the first percept is received
	}

	public PacAgent(int id)
	{
		this("PacAgent" + id);
	}

	public void see(Percept p)
	{
		// check/convert type
		if (!(p instanceof PacPercept))
		{
			System.err.println("ERROR: Invalid percept: " + p);
			return;
		}
		PacPercept percept = (PacPercept)p;

		// initialize the world map once we know the world size
		if (world == null) world = new World(percept.getWorldSize());

		/* XXX messages are delivered *in order* e.g. the first agent won't see
		 * any messages on the first turn, the last agent will see all
		 * messages.
		 *
		 * But I think this really only affects the logic of the first turn, in
		 * the long run it works out.
		 */

		// copy some parts of the percept for convenience
		held_package = percept.getHeldPackage();
		bumped = percept.feelBump();
		List<Message> messages = new ArrayList<Message>();
		for (String message : percept.getMessages())
		{
			messages.add(Message.fromString(message));
		}

		// reset other agent positions, we only care about current positions
		for (PacAgent agent : agents.values()) agent.pos = null;

		// update positions of visible agents (including this agent)
		for (VisibleAgent agent : percept.getVisAgents())
		{
			if (agent.getId().equals(id))
			{
				pos = new Coord(agent.getX(), agent.getY());
			}
			else
			{
				otherAgent(agent.getId()).pos = new Coord(agent.getX(), agent.getY());
			}
		}

		// update goals and positions transmitted by other agents
		for (Message message : messages)
		{
			if (message.goal != null && !id.equals(message.id))
			{
				otherAgent(message.id).goal = message.goal;
				otherAgent(message.id).pos = message.pos;
			}

			if (message.holding != -2 && !id.equals(message.id))
			{
				otherAgent(message.id).holding = message.holding;
				// other agent's goal should be null, this will be updated via shared_discoveries
			}

			if (message.dropped_package != null)
				dropped_packages.add(message.dropped_package);

			if (message.pickup_dropped != null)
				dropped_packages.remove(message.pickup_dropped);
		}

		resolveGoalConflicts();

		// what do we know about the world from this percept?
		boolean known[][] = new boolean[world.getSize()][world.getSize()];
		for (int i = 0; i < world.getSize(); ++i)
			for (int j = 0; j < world.getSize(); ++j)
				known[i][j] = false;

		// start by assuming that everything around the agent is known to be clear
		for (int i = Math.max(pos.x - vis_radius, 0); i <= Math.min(pos.x + vis_radius, world.getSize() - 1); ++i)
			for (int j = Math.max(pos.y - vis_radius, 0); j <= Math.min(pos.y + vis_radius, world.getSize() - 1); ++j)
				known[i][j] = true;

		// anywhere we see a package is an area of uncertainty
		for (VisiblePackage pack : percept.getVisPackages())
		{
			int px = pack.getX();
			int py = pack.getY();

			dropoffs.add(new Coord(pack.getDestX(), pack.getDestY()));

			for (int i = -1; i <= 1; ++i)
				for (int j = -1; j <= 1; ++j)
					if (world.in_bounds(px + i, py + j))
						known[px + i][py + j] = false;
		}

		// but anywhere we see an agent is certainly clear
		for (PacAgent agent : agents.values())
			if (agent.pos != null)
				known[agent.pos.x][agent.pos.y] = true;

		// we also know clear spaces that other agents tell us
		// anything we receive goes in shared_discoveries
		for (Message message : messages)
		{
			if (message.coords == null) continue;
			for (Coord c : message.coords)
			{
				known[c.x][c.y] = true;
				shared_discoveries.add(c);
			}
		}

		// if we have a goal and a package, we must have picked it up, so that spot is now clear
		if (goal != null && held_package != null)
		{
			known[goal.x][goal.y] = true;
		}

		// now update our world map of clear spaces
		for (int i = 0; i < world.getSize(); ++i)
		{
			for (int j = 0; j < world.getSize(); ++j)
			{
				if (known[i][j])
				{
					world.clear(i, j);
					discoveries.add(new Coord(i, j));
				}
			}
		}

		for (Coord c : shared_discoveries)
		{
			// remove public knowledge from our list of discoveries
			discoveries.remove(c);

			// and also from agents' goals
			for (PacAgent agent : agents.values())
				if (c.equals(agent.goal) && !dropped_packages.contains(c))
					agent.goal = null;
		}

		System.out.println(this);
		System.out.println("OTHER AGENTS:");
		for (PacAgent agent : agents.values())
		{
			System.out.println(agent);
		}
		System.out.print("\n");

		//System.out.println(world);
	}

	// simple subsumption architecture
	public Action selectAction()
	{
		Action action = null;

		// first ensure important information is shared
		action = communicate();
		if (action != null) return action;

		// then make sure held packages are delivered
		action = deliver();
		if (action != null) return action;

		// then try to pick up adjacent packages
		action = pickup();
		if (action != null) return action;

		// then explore for new packages
		action = explore();
		if (action != null) return action;

		// shouldn't get here
		System.out.println("No action returned by any layer");
		return new Idle();
	}

	Action communicate()
	{
		if (dropped_package != null)
		{
			Message message = new Message();
			message.dropped_package = dropped_package;
			dropped_package = null;
			flushDiscoveries(message);

			return new Say(message.toString());
		}

		// if we've picked up or dropped off a package, broadcast discoveries
		// and indicate how we are holding a package
		if ((goal != null && held_package != null) || delivered)
		{
			Message message = new Message();

			if (goal != null)
			{
				world.clear(goal.x, goal.y);
				if (dropped_packages.contains(goal))
				{
					dropped_packages.remove(goal);
					message.pickup_dropped = goal;
				}
				else
					discoveries.add(goal);
			}

			goal = null;
			delivered = false;

			message.id = id;
			if (held_package == null)
				message.holding = -1;
			else
				message.holding = pos.dirTo(new Coord(held_package.getX(), held_package.getY()));
			flushDiscoveries(message);

			return new Say(message.toString());
		}

		return null;
	}

	Action deliver()
	{
		if (held_package == null) return null;

		Coord dropoff = new Coord(held_package.getDestX(), held_package.getDestY());

		// drop off package
		// TODO need to ensure that the delivery space is empty
		if (pos.dist(dropoff) == 1)
		{
			delivered = true;
			return new Dropoff(pos.dirTo(dropoff));
		}

		// treat nearby unknown spaces as obstacles
		Set<Coord> obstacles = new HashSet<Coord>();
		for (int i = Math.max(pos.x - vis_radius, 0); i <= Math.min(pos.x + vis_radius, world.getSize() - 1); ++i)
		{
			for (int j = Math.max(pos.y - vis_radius, 0); j <= Math.min(pos.y + vis_radius, world.getSize() - 1); ++j)
			{
				Coord c = new Coord(i, j);
				if (world.at(c) == World.Space.UNKNOWN)
					obstacles.add(c);
			}
		}
		// navigate around dropped packages
		for (Coord c : dropped_packages)
		{
			obstacles.add(c);
		}
		// treat other agents, their packages, and goals as obstacles
		for (PacAgent agent : agents.values())
		{
			if (agent.pos != null)
			{
				obstacles.add(agent.pos);
				if (agent.holding != -1)
				{
					Coord c = agent.pos.shift(agent.holding);
					obstacles.add(c);
				}
			}
			if (agent.goal != null)
				obstacles.add(agent.goal);
		}
		// other package's delivery spots are obstacles
		for (Coord c : dropoffs)
		{
			if (!c.equals(new Coord(held_package.getDestX(), held_package.getDestY())))
				obstacles.add(c);
		}

		int dir = -1;
		if (pos.dist(dropoff) >= vis_radius || world.at(dropoff) != World.Space.UNKNOWN)
		{
			// go to the dropoff
			dir = world.shortestPathDir(pos, dropoff, pos.dirTo(new Coord(held_package.getX(), held_package.getY())), obstacles, 1);
		}

		// if we can't get there
		if (dir == -1)
		{
			Coord c;

			// try to find a clear space to drop the package
			List<Coord> ds = new ArrayList<Coord>();
			c = pos.shift(Direction.NORTH);
			if (world.in_bounds(c) && !obstacles.contains(c)) ds.add(c);
			c = pos.shift(Direction.EAST);
			if (world.in_bounds(c) && !obstacles.contains(c)) ds.add(c);
			c = pos.shift(Direction.SOUTH);
			if (world.in_bounds(c) && !obstacles.contains(c)) ds.add(c);
			c = pos.shift(Direction.WEST);
			if (world.in_bounds(c) && !obstacles.contains(c)) ds.add(c);

			if (ds.size() == 0)
			{
				// TODO try to get to a clear space?
				System.err.println(id + ": no path from " + pos + " to dropoff " + dropoff);
				return new Idle();
			}
			else
			{
				// TODO it is possible that the agent will trap itself here
				Coord dp = ds.get(ThreadLocalRandom.current().nextInt(0, ds.size()));
				dropped_package = dp;
				dropped_packages.add(dp);
				return new Dropoff(pos.dirTo(dp));
			}
		}

		return new Move(dir);
	}

	Action pickup()
	{
		if (possible_package != -1 && bumped)
		{
			// XXX it *must* be a package, since the other agents should avoid our goal
			int dir = possible_package;
			possible_package = -1;
			return new Pickup(dir);
		}

		return null;
	}

	Action explore()
	{
		// if we reached our goal, it was just an empty space
		if (pos.equals(goal))
		{
			discoveries.add(goal);
			goal = null;
		}

		// if no goal, set goal
		if (goal == null)
		{
			// TODO it should be possible to steal goals if you are closer than they are

			// get other agents' goals so we can avoid them
			Set<Coord> avoid = new HashSet<Coord>();
			for (PacAgent agent : agents.values())
				if (agent.goal != null)
					avoid.add(agent.goal);

			goal = world.nearestUnknown(pos, avoid);

			if (goal == null)
			{
				Coord c = null;
				for (Coord d : dropped_packages)
				{
					boolean taken = false;
					for (PacAgent agent : agents.values())
					{
						if (d.equals(agent.goal))
						{
							taken = true;
							break;
						}
					}
					if (taken) continue;
					if (c == null || pos.dist(d) < pos.dist(c))
						c = d;
				}

				if (c == null)
				{
					System.out.println("No dropped packages or unknown space to get");
					return new Idle();
				}
				else
				{
					goal = c;
				}
			}

			Message message = new Message();
			// broadcast our goal and current position
			message.id = id;
			message.goal = goal;
			message.pos = pos;
			// throw in discoveries because why not?
			flushDiscoveries(message);

			return new Say(message.toString());
		}

		// get direction to goal

		// treat nearby unknown spaces as obstacles
		Set<Coord> obstacles = new HashSet<Coord>();
		for (int i = Math.max(pos.x - vis_radius, 0); i <= Math.min(pos.x + vis_radius, world.getSize() - 1); ++i)
		{
			for (int j = Math.max(pos.y - vis_radius, 0); j <= Math.min(pos.y + vis_radius, world.getSize() - 1); ++j)
			{
				Coord c = new Coord(i, j);
				if (!c.equals(goal) && world.at(c) == World.Space.UNKNOWN)
					obstacles.add(c);
			}
		}
		// avoid dropped packages
		for (Coord c : dropped_packages)
		{
			if (!c.equals(goal)) obstacles.add(c);
		}
		// treat other agents, their packages, and goals as obstacles
		for (PacAgent agent : agents.values())
		{
			if (agent.pos != null)
			{
				obstacles.add(agent.pos);
				if (agent.holding != -1) obstacles.add(agent.pos.shift(agent.holding));
			}
			if (agent.goal != null) obstacles.add(agent.goal);
		}

		// XXX we should not be holding a package at this point
		int dir = world.shortestPathDir(pos, goal, -1, obstacles, 0);

		if (dir == -1)
		{
			// TODO better way to handle this?
			System.out.println(id + ": no path to nearest unexplored space");
			goal = null;
			return new Idle();
		}

		Coord next = pos.shift(dir);

		// if we're right next to the unknown space, it could be a package
		if (goal.equals(next)) possible_package = dir;

		return new Move(dir);
	}

	// add all discoveries to the message (and to shared_disc)
	void flushDiscoveries(Message message)
	{
		if (discoveries.size() == 0) return;
		message.coords = new ArrayList<Coord>();
		for (Coord c : discoveries)
		{
			message.coords.add(c);
			shared_discoveries.add(c);
		}
	}

	// get the PacAgent for the id, initializing a new empty agent if necessary
	PacAgent otherAgent(String id)
	{
		if (!agents.containsKey(id)) agents.put(id, new PacAgent(id));
		return agents.get(id);
	}

	/* After agents have broadcasted their goals, there is a deterministic way
	 * to determine which goals are assigned to which agent.
	 * This resolution is performed independently by each agent so that they
	 * all get the same result and are aware of each other's goals.
	 */
	void resolveGoalConflicts()
	{
		// first collect all agents and goals
		Set<Coord> goals = new HashSet<Coord>();
		List<PacAgent> all_agents = new ArrayList<PacAgent>();
		if (goal != null)
		{
			goals.add(goal);
			all_agents.add(this);
		}
		for (PacAgent agent : agents.values())
		{
			if (agent.goal != null && agent.pos != null)
			{
				goals.add(agent.goal);
				all_agents.add(agent);
			}
		}

		for (PacAgent agent : all_agents)
		{
			if (agent.goal != null && agent.pos == null)
				System.out.println(agent.id + " has null pos");
		}

		// no chance of conflict
		if (goals.size() < 2 || agents.size() < 2) return;

		for (Coord g : goals)
		{
			// get the positions of all agents with that goal
			List<Coord> poss = new ArrayList<Coord>();
			for (PacAgent agent : all_agents)
				if (g.equals(agent.goal)) poss.add(agent.pos);

			// no conflict, nothing to do
			if (poss.size() == 1) continue;
			System.out.println(id + " resolving conflict for goal " + g);

			// sort positions by distance to the goal (breaking ties in an arbitrary but deterministic way)
			poss.sort((c1, c2) -> {
				if (c1.dist(g) < c2.dist(g)) return -1;
				if (c1.dist(g) > c2.dist(g)) return 1;
				if (c1.x < c2.x) return -1;
				if (c1.x > c2.x) return 1;
				if (c1.y < c2.y) return -1;
				if (c1.y > c2.y) return 1;
				// shouldn't reach here
				return 0;
			});

			// clear goals of all but the one closest agent
			for (PacAgent agent : all_agents)
			{
				if (g.equals(agent.goal))
				{
					if (!agent.pos.equals(poss.get(0)))
						agent.goal = null;
					else
					{
						System.out.println(agent.id + " wins goal" + agent.goal);
					}
				}
			}
		}
	}

	@Override
	public String toString()
	{
		String str = new String(id);
		str += "\npos: ";
		if (pos == null)
			str += "unknown";
		else
			str += pos.toString();
		str += "\ngoal: ";
		if (goal == null)
			str += "none";
		else
			str += goal.toString();
		str += "\nholding: ";
		if (holding == -1)
			str += "nothing";
		else
			str += Direction.toString(holding);
		return str;
	}
}
