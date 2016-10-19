package mba210;

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
}
