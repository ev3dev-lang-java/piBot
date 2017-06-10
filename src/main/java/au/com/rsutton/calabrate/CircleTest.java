package au.com.rsutton.calabrate;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.pi4j.gpio.extension.adafruit.GyroProvider;
import com.pi4j.gpio.extension.grovePi.GrovePiPin;
import com.pi4j.gpio.extension.grovePi.GrovePiProvider;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

import au.com.rsutton.config.Config;
import au.com.rsutton.entryPoint.controllers.HeadingHelper;
import au.com.rsutton.entryPoint.units.Distance;
import au.com.rsutton.entryPoint.units.DistanceUnit;
import au.com.rsutton.entryPoint.units.Speed;
import au.com.rsutton.entryPoint.units.Time;
import au.com.rsutton.hazelcast.SetMotion;
import au.com.rsutton.i2c.I2cSettings;
import au.com.rsutton.robot.rover.Angle;
import au.com.rsutton.robot.rover.AngleUnits;
import au.com.rsutton.robot.rover.DeadReconing;
import au.com.rsutton.robot.rover.SpeedHeadingController;
import au.com.rsutton.robot.rover.WheelController;
import au.com.rsutton.robot.rover.WheelFactory;

public class CircleTest implements Runnable
{

	private GrovePiProvider grove;
	private WheelController rightWheel;
	private WheelController leftWheel;
	private DeadReconing reconing;
	private SpeedHeadingController speedHeadingController;

	public CircleTest() throws IOException, InterruptedException, UnsupportedBusNumberException
	{
		Config config = new Config();

		grove = new GrovePiProvider(I2cSettings.busNumber, 4);

		grove.setMode(GrovePiPin.GPIO_A1, PinMode.ANALOG_INPUT);

		rightWheel = WheelFactory.setupRightWheel(grove, config);

		leftWheel = WheelFactory.setupLeftWheel(grove, config);

		GyroProvider gyro = new GyroProvider(I2cSettings.busNumber, GyroProvider.Addr);

		Angle initialAngle = new Angle(0, AngleUnits.DEGREES);
		reconing = new DeadReconing(initialAngle, gyro);

		while (!gyro.isCalabrated())
		{
			Thread.sleep(100);
		}

		speedHeadingController = new SpeedHeadingController(rightWheel, leftWheel, 0);

		Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this, 200, 200, TimeUnit.MILLISECONDS);

		double h = 0;
		SetMotion motion = new SetMotion();
		for (double i = 90; i <= 360; i += 20)
		{

			motion.setChangeHeading(20.0);
			motion.setSpeed(new Speed(new Distance(10, DistanceUnit.CM), Time.perSecond()));
			speedHeadingController.setDesiredMotion(motion);
			while (Math.abs(HeadingHelper.getChangeInHeading(h, reconing.getHeading().getHeading())) > 10)
			{
				Thread.sleep(1000);
			}
		}
		while (Math.abs(HeadingHelper.getChangeInHeading(h, reconing.getHeading().getHeading())) > 5)
		{
			Thread.sleep(1000);
		}

		motion.setSpeed(new Speed(new Distance(0, DistanceUnit.CM), Time.perSecond()));
		speedHeadingController.setDesiredMotion(motion);
		Thread.sleep(20000);
		System.exit(0);

	}

	@Override
	public void run()
	{
		try
		{

			reconing.updateLocation(rightWheel.getDistance(), leftWheel.getDistance());

			speedHeadingController.setActualHeading(reconing.getHeading());

		} catch (Exception e)
		{
		}
	}
}