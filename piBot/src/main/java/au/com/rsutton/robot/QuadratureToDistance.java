package au.com.rsutton.robot;

import au.com.rsutton.entryPoint.units.Distance;
import au.com.rsutton.entryPoint.units.DistanceUnit;

public class QuadratureToDistance
{

	private double scale = 715d/222.0d ; // quadrature/mm

	Distance scale(Integer quadrature)
	{
		if (quadrature == null)
			return null;
		return new Distance(quadrature * scale, DistanceUnit.MM);
	}
}