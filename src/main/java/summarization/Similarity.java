package summarization;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class Similarity {

	public void normalization(Map<Long, Long> ts1) {

	}

	public static double cosine(Map<Long, Long> ts1, Map<Long, Long> ts2) {
		Set<Long> axis = new TreeSet<Long>();
		axis.addAll(ts1.keySet());
		axis.addAll(ts2.keySet());
		double numerator = 0f;
		double denominator = 0f;
		double dPart = 0;
		for (Long cur : axis) {
			if (ts1.containsKey(cur))
				dPart += ts1.get(cur);

			long val1 = 0;
			if (ts1.containsKey(cur)) {
				val1 = ts1.get(cur);
			}
			long val2 = 0;
			if (ts2.containsKey(cur)) {
				val2 = ts2.get(cur);
			}
			numerator += val1 * val2;
		}
		denominator = Math.sqrt(dPart);
		dPart = 0;
		for (Long cur : axis) {
			if (ts2.containsKey(cur))
				dPart += ts2.get(cur);
		}
		denominator += Math.sqrt(dPart);
		numerator = Math.sqrt(numerator);
		return denominator / numerator;
	}

}
