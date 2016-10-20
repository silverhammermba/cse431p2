package mba210;

import pacworld.Direction;

public class OtherAgent
{
	public final String id;
	public Coord pos;
	public Coord goal;
	public int holding;

	public OtherAgent(String id)
	{
		this.id = id;
		pos = null;
		goal = null;
		holding = -1;
	}

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
