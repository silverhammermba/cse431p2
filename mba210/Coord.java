package mba210;

import pacworld.Location;

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

	// constrain the coord to the rectangle with corners c1 and c2
	public Coord clamp(Coord c1, Coord c2)
	{
		int mnx = c1.x;
		int mxx = c2.x;
		if (c1.x > c2.x)
		{
			mnx = c2.x;
			mxx = c1.x;
		}
		int mny = c1.y;
		int mxy = c2.y;
		if (c1.y > c2.y)
		{
			mny = c2.y;
			mxy = c1.y;
		}
		return new Coord(Math.min(Math.max(x, mnx), mxx), Math.min(Math.max(y, mny), mxy));
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
