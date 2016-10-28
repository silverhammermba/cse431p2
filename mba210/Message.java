package mba210;

import java.util.ArrayList;
import java.util.List;

// Represents messages that PacAgents send to each other
public class Message
{
	/* Public fields because agents constructing messages can set any
	 * combination of fields to whatever they like.
	 */
	public String id;
	public Coord goal;
	public Coord pos;
	public Coord dropped_package;
	public Coord pickup_dropped;
	public Integer holding;
	public List<Coord> coords;

	// deserialize a Message object
	public static Message fromString(String str)
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

	// encode integers as (printable) unicode characters. this works up to about 6000-ish
	private String encodeInt(int c)
	{
		return "" + (char)('!' + c);
	}

	// inverse of encodeInt
	private static int decodeInt(char c)
	{
		return c - '!';
	}

	// shortcut for encoding x and y of a Coord
	private String encodeCoord(Coord c)
	{
		return encodeInt(c.x) + encodeInt(c.y);
	}

	// serialize
	@Override
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
		if (holding != null)
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
