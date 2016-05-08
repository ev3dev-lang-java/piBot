package com.pi4j.gpio.extension.pixy;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PixyLaserRangeService implements Runnable
{

	private PixyCmu5 pixy;

	AtomicReference<Collection<Coordinate>> availableData = new AtomicReference<>();

	int[] allowedAngles = null;

	private final static Object sync = new Object();

	public PixyLaserRangeService(int[] allowedAngles) throws IOException
	{
		this.allowedAngles = allowedAngles;
		availableData.set(new LinkedList<Coordinate>());
		pixy = new PixyCmu5();
		pixy.setup();

		Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this,
				10000, 250, TimeUnit.MILLISECONDS);

	}

	public Collection<Coordinate> getCurrentData()
	{
		synchronized (sync)
		{
			Collection<Coordinate> data = availableData.get();
			availableData.set(new LinkedList<Coordinate>());
			return data;
		}
	}

	@Override
	public void run()
	{

		try
		{
			List<Frame> frames = pixy.getFrames();
			System.out.println("Got " + frames.size());
			List<Coordinate> coords = new LinkedList<Coordinate>();

			// System.out.println("pixy frames = " + frames.size());
			for (Frame frame : frames)
			{
				if (frame.yCenter > PixyLaserRange.Y_CENTER && frame.height > 0)
				{
					Coordinate coord = new Coordinate(frame.xCenter,
							frame.yCenter);
					boolean found = false;
					for (Coordinate knownCoord : coords)
					{
						if (coord.x > knownCoord.getAverageX() - 10
								&& coord.x < knownCoord.getAverageX() + 10)
						{
							knownCoord.y += coord.y;
							knownCoord.x += coord.x;
							knownCoord.count++;
							found = true;
							break;
						}
					}
					if (!found)
					{
						coords.add(coord);
					}

				}
			}

			List<Coordinate> result = new LinkedList<Coordinate>();

			for (Coordinate coord : coords)
			{
				if (coord.count > 1)
				{
					result.add(coord);
				}
			}

			synchronized (sync)
			{
				availableData.set(result);
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

}
