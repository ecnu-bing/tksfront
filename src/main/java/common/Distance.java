package common;

import weka.core.Instance;

public interface Distance {
	public double dist(Instance a, Instance b);
}
