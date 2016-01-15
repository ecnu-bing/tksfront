package summarization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import util.DateUtil;

/**
 * 用于表示sparse的time series
 * 
 * @author xiafan
 *
 */
public class TimeSeries {
	TreeMap<Long, Float> baseTs = new TreeMap<Long, Float>();
	private long startTime = Long.MAX_VALUE;
	private long endTime = Long.MIN_VALUE;

	public TimeSeries() {
	}

	public TimeSeries(List<long[]> ts, long startTime, long endTime) {
		for (long[] point : ts) {
			baseTs.put(point[0], (float) point[1]);
		}
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public TimeSeries(Map<Long, Long> ts, long startTime, long endTime) {
		for (Entry<Long, Long> point : ts.entrySet()) {
			baseTs.put(point.getKey(), (float) point.getValue());
		}
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public float getValueAt(long time) {
		if (baseTs.containsKey(time)) {
			return baseTs.get(time);
		} else {
			return 0f;
		}
	}

	public void addValueAtTime(long time, float val) {
		if (baseTs.containsKey(time)) {
			baseTs.put(time, baseTs.get(time) + val);
		} else {
			baseTs.put(time, val);
		}
	}

	public long getStartTime() {
		return startTime;
	}

	public long getEndTime() {
		return endTime;
	}

	public double[] toArray() {
		double[] ret = new double[(int) (endTime - startTime) / DateUtil.HOUR_GRANU + 1];
		long curTime = startTime;
		int i = 0;
		while (curTime <= endTime) {
			ret[i++] = getValueAt(curTime);
			curTime += DateUtil.HOUR_GRANU;
		}
		return ret;
	}

	public void merge(TimeSeries series) {
		for (Entry<Long, Float> entry : series.baseTs.entrySet()) {
			addValueAtTime(entry.getKey(), entry.getValue());
		}
		startTime = Math.min(startTime, series.startTime);
		endTime = Math.max(endTime, series.endTime);
	}

	public float maxValue() {
		if (baseTs.size() > 0) {
			return Collections.max(baseTs.values());
		}
		return 0f;
	}

	public static TimeSeries merge(List<TimeSeries> series) {
		TimeSeries ret = new TimeSeries();
		for (TimeSeries ser : series) {
			ret.merge(ser);
		}
		return ret;
	}

	public static Long[] outlineRange(TimeSeries ser) {
		float max = ser.maxValue();

		long start = ser.getStartTime();
		List<Long[]> intervals = new ArrayList<Long[]>();
		while (start <= ser.getEndTime()) {
			long curStart = start;
			while (start <= ser.getEndTime()) {
				if (ser.getValueAt(start) < max * 0.2) {
					break;
				}
				start += DateUtil.HOUR_GRANU;
			}
			if (curStart > start)
				intervals.add(new Long[] { curStart, start - DateUtil.HOUR_GRANU });
			if (ser.getValueAt(start) < max * 0.2) {
				start += DateUtil.HOUR_GRANU;
			}
		}

		Collections.sort(intervals, new Comparator<Long[]>() {

			@Override
			public int compare(Long[] o1, Long[] o2) {
				int len1 = (int) (o1[1] - o1[0]);
				int len2 = (int) (o2[1] - o2[0]);
				int ret = Integer.compare(len2, len1);
				if (ret == 0) {
					ret = Long.compare(o1[0], o2[0]);
				}
				return ret;
			}

		});
		if (intervals.size() > 0)
			return intervals.get(0);
		else
			return new Long[] { ser.getStartTime(), ser.getEndTime() };
	}
}
