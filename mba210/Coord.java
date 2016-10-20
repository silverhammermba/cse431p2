package mba210;

import pacworld.Location;
import pacworld.Direction;

import java.util.Objects;

public class Coord
{
	public final int x;
	public final int y;

	public Coord(int x, int y)
	{
		this.x = x;
		this.y = y;
	}

	public Coord(Location l)
	{
		x = l.getX();
		y = l.getY();
	}

	// get the (Manhattan) distance between two coords
	public int dist(Coord c)
	{
		return Math.abs(x - c.x) + Math.abs(y - c.y);
	}

	public int dirTo(Coord c)
	{
		if (c.x < x) return Direction.WEST;
		if (c.y < y) return Direction.NORTH;
		if (c.x > x) return Direction.EAST;
		if (c.y > y) return Direction.SOUTH;
		return -1;
	}

	public Coord shift(int d)
	{
		return new Coord(x + Direction.DELTA_X[d], y + Direction.DELTA_Y[d]);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Coord)) return false;
		Coord c = (Coord)o;
		return x == c.x && y == c.y;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(x, y);
	}

	@Override
	public String toString()
	{
		return "(" + x + "," + y + ")";
	}
}
