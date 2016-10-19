package mba210;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;
import java.util.List;

public class Message implements Serializable
{
	public Coord goal;
	public Coord pos;
	public List<Coord> coords;

	// TODO can probably make fromString/toString more efficient
	static Message fromString(String str)
	{
		Message message;

		try
		{
			InputStream sis = new ByteArrayInputStream(Base64.getDecoder().decode(str));
			ObjectInputStream ois = new ObjectInputStream(sis);
			message = (Message)ois.readObject();
			ois.close();
			sis.close();
		}
		catch (Exception ex)
		{
			System.err.println("Exception during message deserialization: " + ex);
			message = new Message();
		}

		return message;
	}

	public String toString()
	{
		String str;

		try
		{
			ByteArrayOutputStream sos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(sos);
			oos.writeObject(this);
			str = Base64.getEncoder().encodeToString(sos.toByteArray());
			oos.close();
			sos.close();
		}
		catch (Exception ex)
		{
			System.err.println("Exception during message serialization: " + ex);
			str = "";
		}

		return str;
	}
}
