package au.com.rsutton.mapping.v3.linearEquasion;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import au.com.rsutton.mapping.v3.impl.ObservedPoint;

public class LinearEquasionFactory
{
	public static LinearEquasion getEquasion(ObservedPoint p1, ObservedPoint p2)
	{
		double run = p2.getX() - p1.getX();
		double rise = p2.getY() - p1.getY();
		
		if (Math.abs(run) < 0.01)
		{
			return new VerticalLine(p1.getX());
		}
		if (Math.abs(rise) < 0.01)
		{
			return new HorizontalLine(p1.getY());
		}
		return new LinearEquasionNormal(p1,p2);
	}
	
	public static LinearEquasion getEquasion(Vector3D p1, Vector3D p2)
	{
		double run = p2.getX() - p1.getX();
		double rise = p2.getY() - p1.getY();
		
		if (Math.abs(run) < 0.01)
		{
			return new VerticalLine(p1.getX());
		}
		if (Math.abs(rise) < 0.01)
		{
			return new HorizontalLine(p1.getY());
		}
		return new LinearEquasionNormal(p1,p2);
	}
}
