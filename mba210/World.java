package mba210;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class World
{
	public enum Space { UNKNOWN, CLEAR };

	int size;
	Space grid[][];

	public World(int size)
	{
		this.size = size;
		grid = new Space[size][size];

		for (int i = 0; i < size; ++i)
			for (int j = 0; j < size; ++j)
				grid[i][j] = Space.UNKNOWN;
	}

	public int getSize()
	{
		return size;
	}

	public Space at(Coord c)
	{
		return grid[c.x][c.y];
	}

	public void clear(int i, int j)
	{
		grid[i][j] = Space.CLEAR;
	}

	public boolean in_bounds(int x, int y)
	{
		return x >= 0 && y >= 0 && x < size && y < size;
	}

	public Coord nearestUnknown(Coord c, Set<Coord> avoid)
	{
		List<Coord> nearest = new ArrayList<Coord>();
		int d = 0;

		for (int i = 0; i < size; ++i)
		{
			for (int j = 0; j < size; ++j)
			{
				Coord g = new Coord(i, j);

				if (avoid.contains(g)) continue;

				if (grid[i][j] == Space.UNKNOWN && (nearest.size() == 0 || c.dist(g) < d))
				{
					nearest = new ArrayList<Coord>();
					nearest.add(g);
					d = c.dist(nearest.get(0));
				}
			}
		}

		if (nearest.size() == 0) return null;

		return nearest.get(ThreadLocalRandom.current().nextInt(0, nearest.size()));
	}

	@Override
	public String toString()
	{
		String str = "";
		for (int j = 0; j < size; ++j)
		{
			for (int i = 0; i < size; ++i)
			{
				if (grid[i][j] == Space.UNKNOWN)
					str += "?";
				else
					str += " ";
			}
			str += "\n";
		}
		return str;
	}
}
