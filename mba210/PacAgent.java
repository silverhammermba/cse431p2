package mba210;
// CSE 431 P2 Maxwell Anselm

import agent.Action;
import agent.Agent;
import agent.Percept;

import pacworld.Dropoff;
import pacworld.Idle;
import pacworld.Location;
import pacworld.Move;
import pacworld.PacPercept;
import pacworld.Pickup;
import pacworld.Say;
import pacworld.VisibleAgent;
import pacworld.VisiblePackage;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;
import java.util.HashSet;

public class PacAgent extends Agent
{
	Coord pos;
	int vis_radius;
	World world;
	Set<Coord> discoveries;
	Set<Coord> shared_disc;

	public PacAgent(int id)
	{
		super(id);
		vis_radius = PacPercept.VIS_RADIUS;
		discoveries = new HashSet<Coord>();
		shared_disc = new HashSet<Coord>();
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
		if (world == null)
		{
			world = new World(percept.getWorldSize());
		}

		for (VisibleAgent agent : percept.getVisAgents())
		{
			if (agent.getId() == id)
			{
				pos = new Coord(agent.getX(), agent.getY());
				System.out.println("agent " + id + " is at " + pos);
			}
		}

		// what do we know about the world from this percept?
		boolean known[][] = new boolean[world.getSize()][world.getSize()];
		for (int i = 0; i < world.getSize(); ++i)
			for (int j = 0; j < world.getSize(); ++j)
				known[i][j] = false;

		// start by assuming that everything around the agent is known to be clear
		for (int i = Math.max(pos.x - vis_radius, 0); i < Math.min(pos.x + vis_radius, world.getSize() - 1); ++i)
			for (int j = Math.max(pos.y - vis_radius, 0); j < Math.min(pos.y + vis_radius, world.getSize() - 1); ++j)
				known[i][j] = true;

		// anywhere we see a package is an area of uncertainty
		for (VisiblePackage pack : percept.getVisPackages())
		{
			int px = pack.getX();
			int py = pack.getY();

			for (int i = -1; i <= 1; ++i)
				for (int j = -1; j <= 1; ++j)
					if (world.in_bounds(px + i, px + j))
						known[px + i][py + j] = false;
		}

		// but anywhere we see an agent is certainly clear
		for (VisibleAgent agent : percept.getVisAgents())
			known[agent.getX()][agent.getY()] = true;

		// TODO and of course we also know clear spaces that other agents tell us
		// and anything we receive goes in shared_disc

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

		for (Coord c : shared_disc) discoveries.remove(c);

		System.out.println(id);
		System.out.println(world);

		/* TODO
		 * because of noise, we are only certain where there are NOT packages.
		 * thus all agents just want to investigate any spaces which they are
		 * not certain about
		 */
	}

	public Action selectAction()
	{
		int dir = ThreadLocalRandom.current().nextInt(0, 4);

		switch (ThreadLocalRandom.current().nextInt(0, 5))
		{
			case 0: return new Dropoff(dir);
			case 1: return new Idle();
			case 2: return new Move(dir);
			case 3: return new Pickup(dir);
			default: return new Say("Hey!");
		}
	}
}
