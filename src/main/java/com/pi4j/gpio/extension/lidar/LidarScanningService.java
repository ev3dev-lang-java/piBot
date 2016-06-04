package com.pi4j.gpio.extension.lidar;

import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import au.com.rsutton.config.Config;
import au.com.rsutton.robot.rover.LidarObservation;
import au.com.rsutton.robot.stepper.StepperMotor;

import com.pi4j.gpio.extension.grovePi.GrovePiProvider;

public class LidarScanningService implements Runnable
{

	private LidarScanner scanner;
	private Thread th;
	private static volatile boolean initialised = false;

	private final static Object sync = new Object();
	volatile boolean stop = false;

	// skip 8 steps between scans
	int scanStepSkip = 8;

	public LidarScanningService(GrovePiProvider grove, StepperMotor stepper, Config config)
			throws InterruptedException, IOException, BrokenBarrierException
	{
		synchronized (sync)
		{
			if (!initialised)
			{
				initialised = true;
				scanner = new LidarScanner(stepper, config, grove);

				th = new Thread(this);
				th.start();
			}
		}
	}

	@Override
	public void run()
	{
		int start = scanner.getMinPosition();
		int end = scanner.getMaxPosition();

		System.out.println("Expect " + (end - start) + " points in a scan");
		while (!stop)
		{
			try
			{
				System.out.println("Start point scan");
				int a = start + 1;
				boolean isStart = true;
				for (; a < end; a += scanStepSkip)
				{
					try
					{
						Vector3D scan = scanner.scan(a);
						if (scan != null)
						{
							publishPoint(scan, isStart);
							isStart = false;
						} else
						{
							System.out.println("Null scan result");
						}

					} catch (IOException e)
					{
						e.printStackTrace();
					} catch (InterruptedException e)
					{
						stop = true;
					}

				}
				a -= scanStepSkip;
				isStart = true;
				for (; a > start; a -= scanStepSkip)
				{
					try
					{

						Vector3D scan = scanner.scan(a);
						if (scan != null)
						{
							publishPoint(scan, isStart);
							isStart = false;
						} else
						{
							System.out.println("Null scan result with sleep");
						}
					} catch (IOException e)
					{
						e.printStackTrace();
					} catch (InterruptedException e)
					{
						stop = true;
					}
				}
			} catch (Throwable e)
			{
				e.printStackTrace();
			}
		}
	}

	private void publishPoint(Vector3D v, boolean isStart)
	{
		Vector3D temp = new Vector3D(v.getX(), v.getY(), 0);
		LidarObservation lo = new LidarObservation(temp, isStart);

		lo.publish();

	}
}
