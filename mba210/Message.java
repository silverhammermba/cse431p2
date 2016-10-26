package mba210;

import java.util.ArrayList;
import java.util.List;

public class Message
{
	public String id;
	public Coord goal;
	public Coord pos;
	public Coord dropped_package;
	public Coord pickup_dropped;
	public int holding;
	public List<Coord> coords;

	public Message()
	{
		// not nullable, needs a default value
		// -1 means "not holding anything", so use -2
		holding = -2;
	}

	static Message fromString(String str)
	{
		Message message = new Message();

		for (int i = 0; i < str.length();)
		{
			switch (str.charAt(i))
			{
				case 'I':
					int end = i + 1;
					for (; str.charAt(end) != '~'; ++end);
					message.id = str.substring(i + 1, end);
					i = end + 1;
					break;
				case 'G':
					message.goal = new Coord(decodeInt(str.charAt(i + 1)), decodeInt(str.charAt(i + 2)));
					i = i + 3;
					break;
				case 'P':
					message.pos = new Coord(decodeInt(str.charAt(i + 1)), decodeInt(str.charAt(i + 2)));
					i = i + 3;
					break;
				case 'D':
					message.dropped_package = new Coord(decodeInt(str.charAt(i + 1)), decodeInt(str.charAt(i + 2)));
					i = i + 3;
					break;
				case 'X':
					message.pickup_dropped = new Coord(decodeInt(str.charAt(i + 1)), decodeInt(str.charAt(i + 2)));
					i = i + 3;
					break;
				case 'H':
					message.holding = decodeInt(str.charAt(i + 1));
					i = i + 2;
					break;
				case 'C':
					message.coords = new ArrayList<Coord>();
					for (i = i + 1; i < str.length(); i += 2)
						message.coords.add(new Coord(decodeInt(str.charAt(i)), decodeInt(str.charAt(i + 1))));
					break;
				default:
					System.err.println("Unrecognized identifier while parsing message " + str.charAt(i) + ": " + str);
			}
		}

		return message;
	}

	private String encodeInt(int c)
	{
		return "" + (char)('!' + c);
	}

	private static int decodeInt(char c)
	{
		return c - '!';
	}

	private String encodeCoord(Coord c)
	{
		return encodeInt(c.x) + encodeInt(c.y);
	}

	public String toString()
	{
		String str = "";

		if (id != null)
		{
			str += "I" + id + "~";
		}
		if (goal != null)
		{
			str += "G" + encodeCoord(goal);
		}
		if (dropped_package != null)
		{
			str += "D" + encodeCoord(dropped_package);
		}
		if (pickup_dropped != null)
		{
			str += "X" + encodeCoord(pickup_dropped);
		}
		if (pos != null)
		{
			str += "P" + encodeCoord(pos);
		}
		if (holding != -2)
		{
			str += "H" + encodeInt(holding);
		}
		if (coords != null && coords.size() > 0)
		{
			str += "C";
			for (Coord c : coords)
				str += encodeCoord(c);
		}

		return str;
	}
}
