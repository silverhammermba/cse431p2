package mba210;

import pacworld.Direction;

import java.util.ArrayList;
import java.util.HashSet;
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

	public Space at(int x, int y)
	{
		return grid[x][y];
	}

	public Space at(Coord c)
	{
		return at(c.x, c.y);
	}

	public void clear(int i, int j)
	{
		grid[i][j] = Space.CLEAR;
	}

	public boolean in_bounds(int x, int y)
	{
		return x >= 0 && y >= 0 && x < size && y < size;
	}

	// find the nearest unknown space to c, excluding any spaces in avoid
	// XXX doesn't take into account obstacles, but eh...
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

	// for doing A* path finding
	class Node
	{
		public Coord pos;
		public Coord pred;
		public int dist;
		public int score;
		public boolean obstacle;

		public Node(int x, int y)
		{
			pos = new Coord(x, y);
			dist = -1;
			obstacle = false;
		}
	}

	/* get the direction to move in on the shortest path from start to end,
	 * taking into account obstacles and a possibly held object
	 */
	public int shortestPathDir(Coord start, Coord end, int hold, Set<Coord> obstacles)
	{
		// create grid of Nodes
		Node nodes[][] = new Node[size][size];
		for (int i = 0; i < size; ++i)
			for (int j = 0; j < size; ++j)
				nodes[i][j] = new Node(i, j);

		// indicate obstacles
		// TODO can be out of bounds???
		for (Coord o : obstacles)
			nodes[o.x][o.y].obstacle = true;

		// get relative position of the held object
		int dx = 0;
		int dy = 0;
		if (hold != -1)
		{
			dx = Direction.DELTA_X[hold];
			dy = Direction.DELTA_Y[hold];
		}

		// set up starting node
		nodes[start.x][start.y].dist = 0;
		nodes[start.x][start.y].score = start.dist(end);

		Set<Node> frontier = new HashSet<Node>();
		Set<Node> visited = new HashSet<Node>();
		frontier.add(nodes[start.x][start.y]);

		while (frontier.size() > 0)
		{
			// find the frontier node with lowest score
			Node c = null;
			for (Node n : frontier)
				if (c == null || n.score < c.score)
					c = n;

			// if we have reached the end, trace backward and find the first move to make
			if (c.pos.equals(end))
			{
				while (!start.equals(c.pred)) c = nodes[c.pred.x][c.pred.y];
				return start.dirTo(c.pos);
			}

			frontier.remove(c);
			visited.add(c);

			// get neighbors (accounting for the held object stopping us from going to the edge)
			List<Node> neighbors = new ArrayList<Node>();
			if (c.pos.x < size - 1 && c.pos.x + dx < size - 1) neighbors.add(nodes[c.pos.x + 1][c.pos.y]);
			if (c.pos.x > 0 && c.pos.x + dx > 0) neighbors.add(nodes[c.pos.x - 1][c.pos.y]);
			if (c.pos.y < size - 1 && c.pos.y + dy < size - 1) neighbors.add(nodes[c.pos.x][c.pos.y + 1]);
			if (c.pos.y > 0 && c.pos.y + dy > 0) neighbors.add(nodes[c.pos.x][c.pos.y - 1]);

			for (Node n : neighbors)
			{
				// if the neighbor was visited, or contains an obstacle (or the space for the held object is an obstacle
				if (visited.contains(n) || n.obstacle || nodes[n.pos.x + dx][n.pos.y + dy].obstacle) continue;

				int new_dist = c.dist + 1;

				// add neighbor to frontier (unless we already have a shorter path to it)
				if (!frontier.contains(n))
					frontier.add(n);
				else if (new_dist >= n.dist)
					continue;

				// update neighbor dist/score
				n.pred = c.pos;
				n.dist = new_dist;
				n.score = new_dist + n.pos.dist(end);
			}
		}

		return -1;
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
					str += ".";
			}
			str += "\n";
		}
		return str;
	}
}
