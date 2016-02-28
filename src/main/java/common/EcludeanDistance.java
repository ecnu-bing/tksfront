package common;

import weka.core.Instance;

public class EcludeanDistance implements Distance {

	@Override
	public double dist(Instance dataA, Instance dataB) {
		double sum = 0;
		for (int i = 0; i < dataA.numAttributes(); i++) {
			double a = Double.isNaN(dataA.value(i)) ? 0 : dataA.value(i);
			double b = Double.isNaN(dataB.value(i)) ? 0 : dataB.value(i);
			sum += Math.pow(a - b, 2.0);
		}
		return Math.sqrt(sum);
	}

}
