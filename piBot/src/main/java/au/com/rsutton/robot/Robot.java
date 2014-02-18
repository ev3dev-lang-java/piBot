package au.com.rsutton.robot;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import au.com.rsutton.entryPoint.controllers.HBridgeController;
import au.com.rsutton.entryPoint.controllers.ServoController;
import au.com.rsutton.entryPoint.controllers.VehicleHeadingController;
import au.com.rsutton.entryPoint.quadrature.QuadratureEncoding;
import au.com.rsutton.entryPoint.quadrature.QuadratureListener;
import au.com.rsutton.entryPoint.units.Distance;
import au.com.rsutton.entryPoint.units.DistanceUnit;
import au.com.rsutton.entryPoint.units.Speed;
import au.com.rsutton.entryPoint.units.Time;
import au.com.rsutton.hazelcast.ResetCoords;
import au.com.rsutton.hazelcast.RobotLocation;
import au.com.rsutton.hazelcast.SetMotion;

import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;
import com.pi4j.gpio.extension.adafruit.Adafruit16PwmPin;
import com.pi4j.gpio.extension.adafruit.Adafruit16PwmProvider;
import com.pi4j.gpio.extension.adafruit.GyroProvider;
import com.pi4j.gpio.extension.adafruit.PwmPin;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.RaspiPin;

public class Robot implements Runnable, HeadingListener
{
	private VehicleHeadingController controller;
	volatile private long lastMessageReceived;
	volatile private long lastLocationPublished;
	//DeadReconingWithGyro reconing = new DeadReconingWithGyro();
	DeadReconingWithQuadrature reconing = new DeadReconingWithQuadrature();
	QuadratureToDistance quadtratureScaler = new QuadratureToDistance();
	final private GyroProvider gyro;
	volatile private int heading;
	DifferentialSpeedMonitor speedMonitor = new DifferentialSpeedMonitor();

	public Robot() throws IOException, InterruptedException
	{
		Thread.sleep(2000);
		gyro = new GyroProvider(1, 0x69);

		Adafruit16PwmProvider provider = setupPwm();

		controller = setupVehicleController(reconing, provider,gyro);

		controller.loadConfig();

		reconing.addHeadingListener(this);

		setupQuadrature();

		createResetCoordsListener();
		createMotionListener();

		Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this,
				300, 300, TimeUnit.MILLISECONDS);

	}

	private void createMotionListener()
	{
		SetMotion message = new SetMotion();
		message.addMessageListener(new MessageListener<SetMotion>()
		{

			@Override
			public void onMessage(Message<SetMotion> message)
			{
				if (lastMessageReceived <= message.getPublishTime())
				{
					lastMessageReceived = message.getPublishTime();
					SetMotion motion = message.getMessageObject();
					try
					{
//						System.out.println("Message received "
//								+ message.getMessageObject().toString());

						controller.setSpeed(motion.getSpeed());
					} catch (InterruptedException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					controller.setHeading(motion.getHeading().intValue());
				}
			}
		});
	}

	private void createResetCoordsListener()
	{
		ResetCoords message = new ResetCoords();
		message.addMessageListener(new MessageListener<ResetCoords>()
		{

			@Override
			public void onMessage(Message<ResetCoords> message)
			{

				ResetCoords messageObject = message.getMessageObject();
				reconing.setCorrectedHeading(messageObject.getHeading());
				reconing.resetLocation(messageObject.getX(),
						messageObject.getY(), messageObject.getHeading());

			}

		});
	}

	private void updateLocation(final Integer leftWheel,
			final Integer rightWheel)
	{

		reconing.updateLocation(quadtratureScaler.scale(leftWheel),
				quadtratureScaler.scale(rightWheel));
		if (lastLocationPublished < System.currentTimeMillis() - 250)
		{
			RobotLocation message = new RobotLocation();
			message.setHeading(heading);
			message.setX(reconing.getX());
			message.setY(reconing.getY());
			message.publish();
			lastLocationPublished = System.currentTimeMillis();

			controller.reportActualSpeed(speedMonitor.getSpeed());
		}
	}

	private void setupQuadrature()
	{
		System.out.println("LHS");
		// LHS
		// 04 = 23 - ok
		// 05 = 24 - ok
		QuadratureEncoding leftWheel = new QuadratureEncoding(RaspiPin.GPIO_04,
				RaspiPin.GPIO_05, true);
		QuadratureListener leftWheelListener = new QuadratureListener()
		{

			@Override
			public void quadraturePosition(int offset)
			{
//				System.out.println("LHS pulse");
				speedMonitor.pulseOnLeft(offset);
				updateLocation(offset, null);

			}

		};
		leftWheel.addListener(leftWheelListener);

		// RHS
		// 03 = 22 - ok
		// 02 = 27 - ok
		QuadratureEncoding rightWheel = new QuadratureEncoding(
				RaspiPin.GPIO_03, RaspiPin.GPIO_02, false);
		QuadratureListener oneWheelListener = new QuadratureListener()
		{

			@Override
			public void quadraturePosition(int offset)
			{
				speedMonitor.pulseOnRight(offset);
				updateLocation(null, offset);

			}
		};
		rightWheel.addListener(oneWheelListener);
	}

	private VehicleHeadingController setupVehicleController(
			HeadingProvider headingProvider, Adafruit16PwmProvider provider, GyroProvider gyro3)
			throws IOException, InterruptedException
	{
		provider.export(Adafruit16PwmPin.GPIO_00, PinMode.PWM_OUTPUT);
		provider.export(Adafruit16PwmPin.GPIO_01, PinMode.PWM_OUTPUT);
		provider.export(Adafruit16PwmPin.GPIO_04, PinMode.PWM_OUTPUT);
		provider.export(Adafruit16PwmPin.GPIO_05, PinMode.PWM_OUTPUT);

		PwmPin leftServoPin = new PwmPin(provider, Adafruit16PwmPin.GPIO_00);
		PwmPin leftDirectionPin = new PwmPin(provider, Adafruit16PwmPin.GPIO_01);

		PwmPin rightServoPin = new PwmPin(provider, Adafruit16PwmPin.GPIO_05);
		PwmPin rightDirectionPin = new PwmPin(provider,
				Adafruit16PwmPin.GPIO_04);

		HBridgeController leftServo = new HBridgeController(leftServoPin,
				leftDirectionPin, ServoController.NORMAL);
		HBridgeController rightServo = new HBridgeController(rightServoPin,
				rightDirectionPin, ServoController.NORMAL);

		leftServo.setOutput(0);
		rightServo.setOutput(0);

		VehicleHeadingController controller = new VehicleHeadingController(
				leftServo, rightServo, headingProvider,gyro);

		controller.autoConfigure(gyro3);
		return controller;
	}

	private Adafruit16PwmProvider setupPwm() throws IOException,
			InterruptedException
	{
		Adafruit16PwmProvider provider = new Adafruit16PwmProvider(1, 0x40);
		provider.setPWMFreq(30);
		return provider;
	}

	@Override
	public void run()
	{
		if (lastMessageReceived < System.currentTimeMillis() - 5000
				&& (controller.getSetSpeed().getSpeed(DistanceUnit.MM,
						TimeUnit.SECONDS) > 1 || controller.getSetSpeed()
						.getSpeed(DistanceUnit.MM, TimeUnit.SECONDS) < 1))
		{
			// no messages for 2 seconds, so stop.
			try
			{
				// System.out.println("Stopping " + lastMessageReceived + " < "
				// + (System.currentTimeMillis() - 5000));
				controller.setSpeed(new Speed(new Distance(0, DistanceUnit.CM),
						new Time(1, TimeUnit.SECONDS)));
			} catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// ensure location updates happen even if we aren't moving
		updateLocation(null, null);

	}

	@Override
	public void headingChanged(int outz)
	{
		heading = outz;

	}

}