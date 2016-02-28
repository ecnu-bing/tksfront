package summarization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.apache.commons.collections.BidiMap;
import org.apache.commons.collections.Factory;
import org.apache.commons.collections.bidimap.DualHashBidiMap;

import collection.DefaultedPutMap;
import common.Distance;
import common.EcludeanDistance;
import weka.core.Instance;
import weka.core.Instances;

public class KMedoidCluster {
	int k = 10;
	int realK;
	int maxIter = 30;
	Set<Integer> medoids;
	Map<Integer, Integer> point2Cluster;
	Instances dataset;

	float cOutlierPercent = 0.9f;
	float nOutlierPercent = 0.9f;
	Distance dist = new EcludeanDistance();
	Set<Integer> outliers = new HashSet<Integer>();

	public KMedoidCluster() {

	}

	public void setK(int k) {
		this.k = k;
	}

	public void cluster(Instances dataset) {
		this.dataset = dataset;
		for (int i = 0; i < maxIter; i++) {
			if (i == 0) {
				initGen();
			} else {
				genCentroids();
			}
			distribute();
		}
	}

	public Map<Integer, Integer> getPoint2Cluster() {
		return point2Cluster;
	}

	public int numberOfClusters() {
		return medoids.size();
	}

	/**
	 * 第一次选择中心采用的是随机的方法
	 */
	private void initGen() {
		int realK = Math.min(dataset.size(), k);
		Random rand = new Random();
		medoids = new HashSet<Integer>();
		point2Cluster = new HashMap<Integer, Integer>();

		while (medoids.size() <= realK) {
			int idx = Math.abs(rand.nextInt()) % dataset.size();
			if (medoids.add(idx)) {
				point2Cluster.put(idx, medoids.size() - 1);
			}
		}
	}

	/**
	 * 每一步中每个cluster产生的新中心是那个到所有节点的距离都最小的那个店
	 */
	private void genCentroids() {
		medoids.clear();
		Map<Integer, BidiMap> cluster2Points = DefaultedPutMap.decorate(new HashMap<Integer, BidiMap>(), new Factory() {
			@Override
			public Object create() {
				return new DualHashBidiMap();
			}
		});

		for (Entry<Integer, Integer> entry : point2Cluster.entrySet()) {
			Map<Integer, Integer> map = cluster2Points.get(entry.getValue());
			map.put(entry.getKey(), map.size());
		}
		point2Cluster.clear();

		for (Entry<Integer, BidiMap> cluster : cluster2Points.entrySet()) {
			genCentroid(cluster);
		}
	}

	private void detectOutlierInCluster(double distMatrix[][], BidiMap points) {
		// compute dist distribute
		List<Double> dists = new ArrayList<Double>();
		for (int i = 0; i < distMatrix.length; i++)
			for (int j = i + 1; j < distMatrix[i].length; j++)
				dists.add(distMatrix[i][j]);
		Collections.sort(dists);

		// select distance cut point
		int cutIdx = (int) Math.ceil(dists.size() * cOutlierPercent);
		if (cutIdx < dists.size()) {
			double cutPoint = dists.get(cutIdx);
			for (Object outObj : points.entrySet()) {
				// detect outliers
				Entry<Integer, Integer> outEntry = (Entry<Integer, Integer>) outObj;
				double nodeDists[] = distMatrix[outEntry.getValue()];
				float outlierEdgeNum = 0;
				for (int nodeIdx = 0; nodeIdx < nodeDists.length; nodeIdx++) {
					if (nodeDists[nodeIdx] >= cutPoint) {
						outlierEdgeNum++;
					}
				}
				if (outlierEdgeNum / nodeDists.length > nOutlierPercent) {
					outliers.add(outEntry.getKey());
				}
			}
		}
	}

	private void genCentroid(Entry<Integer, BidiMap> cluster) {
		// compute dist matrix
		double distMatrix[][] = new double[cluster.getValue().size()][cluster.getValue().size()];
		for (Object outObj : cluster.getValue().entrySet()) {
			Entry<Integer, Integer> outEntry = (Entry<Integer, Integer>) outObj;
			for (Object innerObj : cluster.getValue().entrySet()) {
				Entry<Integer, Integer> innerEntry = (Entry<Integer, Integer>) innerObj;
				double curDist = dist.dist(dataset.instance(outEntry.getKey()), dataset.instance(innerEntry.getKey()));
				distMatrix[outEntry.getValue()][innerEntry.getValue()] = curDist;
				distMatrix[innerEntry.getValue()][outEntry.getValue()] = curDist;
			}
		}

		// detect outliers
		detectOutlierInCluster(distMatrix, cluster.getValue());

		// generate new centroid
		int cenIdx = -1;
		double minDist = Double.MAX_VALUE;
		for (Object outObj : cluster.getValue().entrySet()) {
			Entry<Integer, Integer> outEntry = (Entry<Integer, Integer>) outObj;
			if (!outliers.contains(outEntry.getKey())) {
				double curDistSum = 0.0;
				for (double curDist : distMatrix[outEntry.getValue()])
					curDistSum += curDist;
				if (curDistSum < minDist) {
					cenIdx = outEntry.getKey();
					minDist = curDistSum;
				}
			}
		}

		if (cenIdx != -1) {
			medoids.add(cenIdx);
			point2Cluster.put(cenIdx, medoids.size() - 1);
		}
	}

	/**
	 * 将节点分发到每个cluster中去
	 */
	private void distribute() {
		for (int i = 0; i < dataset.size(); i++) {
			if (!outliers.contains(i)) {
				Instance curData = dataset.get(i);
				int minIdx = -1;
				double minDist = Double.MAX_VALUE;
				for (Integer mediod : medoids) {
					double curDist = dist.dist(curData, dataset.get(mediod));
					if (curDist < minDist) {
						minIdx = mediod;
					}
				}
				point2Cluster.put(i, minIdx);
			}
		}
	}

	public static void main(String[] args) {

	}
}
