package mba210;
// CSE 431 P2 Maxwell Anselm

import pacworld.Direction;

import java.util.Objects;

// utility class for an (x,y) coordinate
public class Coord
{
	public final int x;
	public final int y;

	public Coord(int x, int y)
	{
		this.x = x;
		this.y = y;
	}

	// the (Manhattan) distance to c from this coord
	public int dist(Coord c)
	{
		return Math.abs(x - c.x) + Math.abs(y - c.y);
	}

	// direction from this coord to another
	// used for adjacent coords, but kinda works in general
	public int dirTo(Coord c)
	{
		if (c.x < x) return Direction.WEST;
		if (c.y < y) return Direction.NORTH;
		if (c.x > x) return Direction.EAST;
		if (c.y > y) return Direction.SOUTH;
		return -1;
	}

	// get the coord in the given direction from this one
	public Coord shift(int d)
	{
		return new Coord(x + Direction.DELTA_X[d], y + Direction.DELTA_Y[d]);
	}

	// need to compare coords a lot
	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Coord)) return false;
		Coord c = (Coord)o;
		return x == c.x && y == c.y;
	}

	// so that they work well in HashSets
	@Override
	public int hashCode()
	{
		return Objects.hash(x, y);
	}

	// (x,y)
	@Override
	public String toString()
	{
		return "(" + x + "," + y + ")";
	}
}
