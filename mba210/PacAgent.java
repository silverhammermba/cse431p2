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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
	Set<Coord> obstacles;
	boolean new_goal;
	int possible_package;

	public PacAgent(int id)
	{
		super(id);
		vis_radius = PacPercept.VIS_RADIUS;
		discoveries = new HashSet<Coord>();
		shared_discoveries = new HashSet<Coord>();
		new_goal = false;
		possible_package = -1;
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

		obstacles = new HashSet<Coord>();

		for (VisibleAgent agent : percept.getVisAgents())
		{
			if (agent.getId() == id)
			{
				pos = new Coord(agent.getX(), agent.getY());
				System.out.println(id + " is at " + pos);
			}
			else
				// TODO take into account held packages
				obstacles.add(new Coord(agent.getX(), agent.getY()));
		}

		obstacles = new HashSet<Coord>();

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
		for (VisibleAgent agent : percept.getVisAgents())
			known[agent.getX()][agent.getY()] = true;

		List<Message> messages = new ArrayList<Message>();

		for (String message : percept.getMessages())
		{
			messages.add(Message.fromString(message));
		}

		// we also know clear spaces that other agents tell us
		// anything we receive goes in shared_discoveries
		for (Message message : messages)
		{
			for (Coord c : message.coords)
			{
				known[c.x][c.y] = true;
				shared_discoveries.add(c);
			}
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
		// and update our list of discoveries (remove things that are already public knowledge)
		for (Coord c : shared_discoveries) discoveries.remove(c);

		if (goal != null && world.at(goal) == World.Space.CLEAR)
		{
			goal = null;
		}

		System.out.println(id);
		System.out.println(world);
	}

	private String newCoordMessage(Set<Coord> coords)
	{
		String message = "W";

		for (Coord coord : coords)
		{
			message += coord.x + "," + coord.y + ";";
		}

		return message;
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

		// then explore for new packages
		action = explore();
		if (action != null) return action;

		// shouldn't get here
		return new Idle();
	}

	Action communicate()
	{
		// TODO if new goal, broadcast that

		// TODO if goal achieved, broadcast discoveries (including cleared goal)

		// if we've discovered a lot, broadcast that
		if (discoveries.size() >= discovery_share_thresh)
		{
			for (Coord c : discoveries)
				shared_discoveries.add(c);

			Message message = new Message();
			message.coords = new ArrayList<Coord>();
			for (Coord c : discoveries)
				message.coords.add(c);

			return new Say(message.toString());
		}

		return null;
	}

	Action deliver()
	{
		// TODO if we're at the goal, drop the package

		// TODO if we're holding a package, determine direction to goal

		// TODO if obstacle, avoid it

		return null;
	}

	Action explore()
	{
		// if no goal, set goal
		if (goal == null)
		{
			new_goal = true;
			goal = world.nearestUnknown(pos);
		}

		// get direction to goal
		int dir = pos.dirTo(goal);
		Coord next = pos.shift(dir);

		// if obstacle, avoid it
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

		// TODO when to pick up package?
		if (possible_package != -1)
		{
		}

		// if we're right next to the unknown space, it could be a package
		if (goal == next)
		{
			possible_package = dir;
		}

		return new Move(dir);
	}
}
