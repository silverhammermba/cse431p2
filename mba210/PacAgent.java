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

public class PacAgent extends Agent
{
	// how many new things we need to know before we send a message
	final int discovery_share_thresh = 10;
	// copied from PacPercept for convenience
	final int vis_radius;

	Coord pos;
	Coord goal;
	World world;
	Set<Coord> discoveries;
	Set<Coord> shared_discoveries;
	int possible_package;
	VisiblePackage held_package;
	boolean bumped;
	Map<String, OtherAgent> agents;

	public PacAgent(int id)
	{
		super(id);
		vis_radius = PacPercept.VIS_RADIUS;
		discoveries = new HashSet<Coord>();
		shared_discoveries = new HashSet<Coord>();
		possible_package = -1;
		agents = new HashMap<String, OtherAgent>();
		// XXX other initialization is done after the first percept is received
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

		// copy some parts of the percept for convenience
		held_package = percept.getHeldPackage();
		bumped = percept.feelBump();
		List<Message> messages = new ArrayList<Message>();
		for (String message : percept.getMessages())
		{
			messages.add(Message.fromString(message));
		}

		// reset other agent positions, we only care about current positions
		for (OtherAgent agent : agents.values()) agent.pos = null;

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

			if (message.holding != -1 && !id.equals(message.id))
			{
				otherAgent(message.id).holding = message.holding;
				// other agent's goal should be null, this will be updated via shared_discoveries
			}
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

			for (int i = -1; i <= 1; ++i)
				for (int j = -1; j <= 1; ++j)
					if (world.in_bounds(px + i, py + j))
						known[px + i][py + j] = false;
		}

		// but anywhere we see an agent is certainly clear
		for (OtherAgent agent : agents.values())
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
			for (OtherAgent agent : agents.values())
				if (c.equals(agent.goal))
					agent.goal = null;
		}

		System.out.println(id + " goal " + goal);
		for (OtherAgent agent : agents.values())
		{
			System.out.println("other" + agent.id + " goal " + agent.goal);
		}

		System.out.println(id);
		System.out.println(world);
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
		return new Idle();
	}

	Action communicate()
	{
		// if goal achieved, broadcast discoveries (including cleared goal) and how we're holding the package
		if (goal != null && held_package != null)
		{
			world.clear(goal.x, goal.y);
			discoveries.add(goal);

			goal = null;

			Message message = new Message();
			message.id = id;
			message.holding = pos.dirTo(new Coord(held_package.getX(), held_package.getY()));
			flushDiscoveries(message);

			return new Say(message.toString());
		}

		return null;
	}

	Action deliver()
	{
		if (held_package == null) return null;

		int holding = pos.dirTo(new Coord(held_package.getX(), held_package.getY()));

		// TODO if we're at the goal, drop the package

		// TODO if we're holding a package, determine direction to goal

		// TODO if obstacle, avoid it

		if (held_package != null) return new Idle();

		return null;
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
			// get other agents' goals so we can avoid them
			Set<Coord> avoid = new HashSet<Coord>();
			for (OtherAgent agent : agents.values())
				if (agent.goal != null)
					avoid.add(agent.goal);

			goal = world.nearestUnknown(pos, avoid);

			Message message = new Message();
			// broadcast our goal and current position
			message.id = id;
			message.goal = goal;
			message.pos = pos;
			// throw in discoveries because why not?
			flushDiscoveries(message);

			return new Say(message.toString());
		}

		System.out.println(id + " pos " + pos + " goal " + goal);

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
		// treat other agents, their packages, and goals as obstacles
		for (OtherAgent agent : agents.values())
		{
			if (agent.pos != null)
			{
				obstacles.add(agent.pos);
				if (agent.holding != -1) obstacles.add(agent.pos.shift(agent.holding));
			}
			if (agent.goal != null) obstacles.add(agent.goal);
		}

		// XXX we should not be holding a package at this point
		int dir = world.shortestPathDir(pos, goal, -1, obstacles);

		if (dir == -1)
		{
			// TODO better way to handle this?
			System.out.println(id + ": no path from " + pos + " to " + goal);
			goal = null;
			return new Idle();
		}

		Coord next = pos.shift(dir);

		// if we're right next to the unknown space, it could be a package
		if (goal.equals(next))
		{
			possible_package = dir;
		}

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

	// get the OtherAgent for the id, initializing a new empty agent if necessary
	OtherAgent otherAgent(String id)
	{
		if (!agents.containsKey(id)) agents.put(id, new OtherAgent(id));
		return agents.get(id);
	}

	/* After agents have broadcasted their goals, there is a deterministic way
	 * to determine which goals are assigned to which agent.
	 * This resolution is performed independently by each agent so that they
	 * all get the same result and are aware of each other's goals.
	 */
	void resolveGoalConflicts()
	{
		// first collect all goals among the agents
		Set<Coord> goals = new HashSet<Coord>();
		if (goal != null) goals.add(goal);
		for (OtherAgent agent : agents.values())
			if (agent.goal != null) goals.add(agent.goal);

		for (Coord g : goals)
		{
			// get the positions of all agents with that goal
			List<Coord> poss = new ArrayList<Coord>();
			if (g.equals(goal)) poss.add(pos);
			for (OtherAgent agent : agents.values())
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
			if (goal.equals(g) && !pos.equals(poss.get(0))) goal = null;
			for (OtherAgent agent : agents.values())
				if (agent.goal.equals(g) && !agent.pos.equals(poss.get(0))) agent.goal = null;
		}
	}
}
