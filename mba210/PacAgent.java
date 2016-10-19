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

			// and also from other agents' goals
			for (OtherAgent agent : agents.values())
				if (c.equals(agent.goal)) agent.goal = null;
		}

		System.out.println(id + " goal " + goal);
		for (OtherAgent agent : agents.values())
		{
			System.out.println("other" + agent.id + " goal " + agent.goal);
		}
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
		// if goal achieved, broadcast discoveries (including cleared goal)
		// TODO should this only be when we have picked up a package?
		if (goal != null && world.at(goal) == World.Space.CLEAR)
		{
			goal = null;

			Message message = new Message();
			// TODO add where we're holding the package

			// discoveries probably contains the (now clear) package location
			flushDiscoveries(message);

			return new Say(message.toString());
		}

		// if we've discovered a lot, broadcast that
		if (discoveries.size() >= discovery_share_thresh)
		{
			Message message = new Message();
			flushDiscoveries(message);

			return new Say(message.toString());
		}

		return null;
	}

	Action deliver()
	{
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
			possible_package = -1;
			return new Pickup(possible_package);
		}

		return null;
	}

	Action explore()
	{
		// if no goal, set goal
		if (goal == null)
		{
			Set<Coord> og = new HashSet<Coord>();
			for (OtherAgent agent : agents.values())
				if (agent.goal != null) og.add(agent.goal);

			goal = world.nearestUnknown(pos, og);

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
		int dir = pos.dirTo(goal);
		Coord next = pos.shift(dir);

		// if obstacle, avoid it
		// TODO need proper path finding around other bots and unknown spaces in range
		/*
		for (Coord o : obstacles)
		{
			if (o == next)
			{
				// always step to the left
				switch (dir)
				{
					case Direction.WEST: return new Move(Direction.SOUTH);
					case Direction.NORTH: return new Move(Direction.WEST);
					case Direction.EAST: return new Move(Direction.NORTH);
					case Direction.SOUTH: return new Move(Direction.EAST);
				}
			}
		}
		*/

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
