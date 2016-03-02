package common;

import weka.core.Instance;

public class CosineDistance implements Distance {

	@Override
	public double dist(Instance a, Instance b) {
		double parta = 0;
		double partb = 0;
		double denominator = 0;
		for (int i = 0; i < a.numAttributes(); i++) {
			denominator = a.value(i) * b.value(i);
			parta += Math.pow(a.value(i), 2);
			partb += Math.pow(b.value(i), 2);
		}
		return 1 - denominator / Math.sqrt(parta * partb);
	}

}
