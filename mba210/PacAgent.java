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
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class PacAgent extends Agent
{
	enum Message { COORDS, UNKNOWN };

	// how many new things we need to know before we send a message
	final int discovery_share_thresh = 10;

	Coord pos;
	final int vis_radius;
	World world;
	Set<Coord> discoveries;
	Set<Coord> shared_discoveries;

	public PacAgent(int id)
	{
		super(id);
		vis_radius = PacPercept.VIS_RADIUS;
		discoveries = new HashSet<Coord>();
		shared_discoveries = new HashSet<Coord>();
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

		for (VisibleAgent agent : percept.getVisAgents())
		{
			if (agent.getId() == id)
			{
				pos = new Coord(agent.getX(), agent.getY());
				System.out.println(id + " is at " + pos);
			}
		}

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
		// TODO keep track of these as obstacles for pathing
		for (VisibleAgent agent : percept.getVisAgents())
			known[agent.getX()][agent.getY()] = true;

		// we also know clear spaces that other agents tell us
		// anything we receive goes in shared_discoveries
		for (String message : percept.getMessages())
		{
			if (messageType(message) == Message.COORDS)
			{
				for (Coord c : parseCoordMessage(message))
				{
					known[c.x][c.y] = true;
					shared_discoveries.add(c);
				}
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

		System.out.println(id);
		System.out.println(world);
	}

	private Message messageType(String message)
	{
		if (message.charAt(0) == 'W')
		{
			return Message.COORDS;
		}

		System.err.println("Unknown message: " + message);
		return Message.UNKNOWN;
	}

	private List<Coord> parseCoordMessage(String message)
	{
		List<Coord> coords = new ArrayList<Coord>();

		Matcher matcher = Pattern.compile("(\\d+),(\\d+);").matcher(message);

		while (matcher.find())
		{
			coords.add(new Coord(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
		}

		return coords;
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

	public Action selectAction()
	{
		if (discoveries.size() >= discovery_share_thresh)
		{
			for (Coord c : discoveries)
				shared_discoveries.add(c);

			return new Say(newCoordMessage(discoveries));
		}

		Coord nearest = world.nearestUnknown(pos);
		System.out.println(id + " at " + pos + " nearest " + nearest);

		if (nearest.x < pos.x) return new Move(Direction.WEST);
		if (nearest.y < pos.y) return new Move(Direction.NORTH);
		if (nearest.x > pos.x) return new Move(Direction.EAST);
		if (nearest.y > pos.y) return new Move(Direction.SOUTH);

		return new Idle();

		/*
		switch (ThreadLocalRandom.current().nextInt(0, 5))
		{
			case 0: return new Dropoff(dir);
			case 1: return new Idle();
			case 2: return new Move(dir);
			case 3: return new Pickup(dir);
			default: return new Say("Hey!");
		}
		*/
	}
}
