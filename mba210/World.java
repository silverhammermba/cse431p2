package mba210;


public class World
{
	public enum Space { UNKNOWN, CLEAR };
	int size;
	Space grid[][];

	public World(int size)
	{
		this.size = size;
		this.grid = new Space[size][size];

		for (int i = 0; i < size; ++i)
			for (int j = 0; j < size; ++j)
				grid[i][j] = Space.UNKNOWN;
	}

	public int getSize()
	{
		return size;
	}

	public void clear(int i, int j)
	{
		grid[i][j] = Space.CLEAR;
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

	public boolean in_bounds(int x, int y)
	{
		return x >= 0 && y >= 0 && x < size && y < size;
	}

	public Coord nearestUnknown(Coord c)
	{
		Coord n = null;
		int d = 0;

		for (int i = 0; i < size; ++i)
		{
			for (int j = 0; j < size; ++j)
			{
				if (grid[i][j] == Space.UNKNOWN && (n == null || c.dist(n) < d))
				{
					n = new Coord(i, j);
					d = c.dist(n);
				}
			}
		}

		return n;
	}
}
