package summarization.outlier;

import java.util.Set;

import weka.core.Instances;

public interface OutlierDetector {
	public Set<Integer> outliers(Instances datatset);
}
