package au.com.rsutton.mapping;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;

import au.com.rsutton.entryPoint.units.DistanceUnit;
import au.com.rsutton.hazelcast.RobotLocation;
import au.com.rsutton.mapping.particleFilter.ScanObservation;
import au.com.rsutton.mapping.v2.Line;

public class Graph extends JPanel implements MessageListener<RobotLocation>
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private MapAccessor map;

	volatile int currentX = 0;
	volatile int currentY = 0;

	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.drawImage(currentImage.get(), 0, 0, this);

	}

	public static void main(String[] args)
	{
		JFrame f = new JFrame();
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		Graph graph = new Graph();
		f.add(graph);
		f.setSize(400, 400);
		f.setLocation(200, 200);
		f.setVisible(true);

		// Thread th = new Thread(graph);
		// th.start();
	}

	public Graph()
	{
		map = new MapAccessor();
		RobotLocation locationMessage = new RobotLocation();
		locationMessage.addMessageListener(this);

	}

	// @Override
	// public void run()
	// {
	// Random rand = new Random();
	// double domain = 600;
	// while (true)
	// {
	// this.repaint();
	// map.addObservation(new ObservationImpl(rand.nextDouble() * domain,
	// rand.nextDouble() * domain, rand.nextDouble()
	// * (domain / 3), LocationStatus.OCCUPIED));
	// try
	// {
	// Thread.sleep(100);
	// } catch (InterruptedException e)
	// {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// }
	// }
	//
	// }

	volatile Double lastHeading = null;

	@Override
	public void onMessage(Message<RobotLocation> message)
	{

		RobotLocation robotLocation = message.getMessageObject();

		try
		{
			List<XY> translatedXyData = new LinkedList<>();

			List<XY> newPoints = new LinkedList<>(translatedXyData);
			lastHeading = robotLocation.getDeadReaconingHeading().getDegrees();

			currentX = (int) (robotLocation.getX().convert(DistanceUnit.CM) * 10d);
			currentY = (int) (robotLocation.getY().convert(DistanceUnit.CM) * 10d);

			for (ScanObservation vector : robotLocation.getObservations())
			{

				XY xy = new XY(vector.getX() * 10, vector.getY() * 10);

				xy = Translator2d.rotate(xy, lastHeading);
				translatedXyData.add(xy);
				newPoints.add(xy);

				map.addObservation(new ObservationImpl(xy.getX() + currentX, xy.getY() + currentY, 1,
						LocationStatus.OCCUPIED));

			}
			List<Line> lines = new LinkedList<>();
			renderMap(newPoints, lines);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		this.repaint();

		// SetMotion message2 = new SetMotion();
		// message2.setSpeed(new Speed(new Distance(0, DistanceUnit.CM),
		// Time
		// .perSecond()));
		//
		// lastHeading += 2;
		// message2.setHeading(lastHeading);
		// message2.publish();
	}

	AtomicReference<BufferedImage> currentImage = new AtomicReference<>();

	void renderMap(List<XY> translatedXyData, List<Line> lines)
	{

		BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = image.createGraphics();
		g2.setColor(new Color(255, 255, 255));

		int h = image.getHeight();
		int w = image.getWidth();

		double minxy = Math.min(h, w);
		double offset = 1500;
		double scale = minxy / (offset * 2.0d);

		// Draw axeX.
		g2.draw(new Line2D.Double(0, (offset * scale), w, (offset * scale)));

		// to
		// make
		// axisX
		// in
		// the
		// middle
		// Draw axeY.
		g2.draw(new Line2D.Double((offset * scale), h, (offset * scale), 0));

		// to
		// make
		// axisY
		// in
		// the
		// middle of the panel

		if (lastHeading != null)
		{
			System.out.println(lastHeading);
			double xbot = (Math.sin(Math.toRadians(lastHeading)) * 400d);// +(Math.cos(Math.toRadians(lastHeading))*10);
			double ybot = -(Math.cos(Math.toRadians(lastHeading)) * 400d);// +(Math.sin(Math.toRadians(lastHeading))*10);

			g2.setColor(new Color(255, 0, 0));
			g2.draw(new Line2D.Double((offset * scale), (int) (offset * scale), (int) ((offset + xbot) * scale),
					(int) ((offset + ybot) * scale)));

		}

		g2.setColor(new Color(255, 255, 255));

		int blockSize = 20;

		for (int x = (int) -offset; x < offset; x += blockSize)
		{
			for (int y = (int) -offset; y < offset; y += blockSize)
			{
				if (!map.isMapLocationClear(x + currentX, -y + currentY, blockSize / 2))
				{
					int r = (int) Math.min(blockSize, (blockSize * scale) / 2);
					g2.drawRect((int) ((x + offset) * scale) - r, (int) ((y + offset) * scale) - r, r * 2, r * 2);

				}
			}
		}

		g2.setColor(new Color(255, 0, 0));
		for (XY xy : translatedXyData)
		{
			int r = (int) Math.min(blockSize, (blockSize * scale) / 2);
			g2.drawRect((int) ((xy.getX() + offset) * scale) - r, (int) ((-xy.getY() + offset) * scale) - r, r * 2,
					r * 2);
		}

		g2.setStroke(new BasicStroke(3));
		if (lines != null)
		{
			g2.setColor(new Color(0, 255, 255));
			for (Line line : lines)
			{
				double x1 = (line.getStart().getX() + offset) * scale;
				double y1 = (-line.getStart().getY() + offset) * scale;
				double x2 = (line.getEnd().getX() + offset) * scale;
				double y2 = (-line.getEnd().getY() + offset) * scale;
				// g2.draw(new Line2D.Double(x1, y1, x2, y2));
				g2.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
				System.out.println("Plot line " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + h + " " + w);

			}
		}
		currentImage.set(image);
	}

	public void clearMap()
	{
		map.qt.clear();

	}

}