package au.com.rsutton.hazelcast;

import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

public class PointCloudMessage extends MessageBase<PointCloudMessage>
{

	private static final long serialVersionUID = 938950572432708619L;

	private long time = System.currentTimeMillis();
	private List<Vector3D> points;

	public PointCloudMessage()
	{
		super(HcTopic.POINT_CLOUD);

	}

	public void setTopic()
	{

		this.topicInstance = HazelCastInstance.getInstance().getTopic(HcTopic.POINT_CLOUD.toString());
	}

	public long getTime()
	{
		return time;
	}

	public void setTime(long time)
	{
		this.time = time;
	}

	public void setPoints(List<Vector3D> points)
	{
		this.points = points;
	}

	public List<Vector3D> getPoints()
	{
		return points;
	}
}
