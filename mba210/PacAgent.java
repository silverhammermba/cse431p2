package mba210;
// CSE 431 P2 Maxwell Anselm

import agent.Action;
import agent.Agent;
import agent.Percept;

import pacworld.Dropoff;
import pacworld.Idle;
import pacworld.Move;
import pacworld.PacPercept;
import pacworld.Pickup;
import pacworld.Say;

import java.util.concurrent.ThreadLocalRandom;

public class PacAgent extends Agent
{
	public PacAgent(int id)
	{
		super(id);
	}

	public void see(Percept p)
	{
		// TODO
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
