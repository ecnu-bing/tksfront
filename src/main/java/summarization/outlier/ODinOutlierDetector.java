package summarization.outlier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.collections.Factory;

import collection.DefaultedPutMap;
import common.ArrayIndexComparator;
import weka.core.Instance;
import weka.core.Instances;

/**
 * 1 计算点点之间的距离 2 运行 connected component算法，找出孤岛，它们是异常点 3
 * 运行odin算法，构建knn图，基于这个图进一步删选异常点
 * 
 * @author xiafan
 *
 */
public class ODinOutlierDetector implements OutlierDetector {
	Instances dataset;
	Set<Integer> outlierSet = new HashSet<Integer>();
	Double[][] distMatrix;
	boolean[][] adj;

	int k = 6;
	int indegree = 0;
	double maxDist = 0;

	private void findOutlierByCC() {
		boolean stop = false;
		while (!stop) {
			distMatrix = new Double[dataset.size()][dataset.size()];
			for (int i = 0; i < dataset.size(); i++) {
				if (!outlierSet.contains(i))
					for (int j = i; j < dataset.size(); j++) {
						if (!outlierSet.contains(j))
							distMatrix[i][j] = dist(dataset.get(i), dataset.get(j));
					}
			}

			DefaultedPutMap<Integer, Integer> counters = DefaultedPutMap.decorate(new HashMap<Integer, Integer>(),
					new Factory() {
						@Override
						public Object create() {
							return 1;
						}
					});
			for (int sc : scc(distMatrix)) {
				counters.put(sc, counters.get(sc) + 1);
			}

			stop = true;
			for (Entry<Integer, Integer> entry : counters.entrySet()) {
				if (entry.getValue() == 1) {
					outlierSet.add(entry.getKey());
					stop = false;
				}
			}
		}
	}

	private int[] scc(Double[][] distMatrix) {
		maxDist = Math.sqrt(dataset.size());
		int[] sc = new int[distMatrix.length];
		Arrays.fill(sc, -1);
		for (int i = 0; i < distMatrix.length; i++) {
			if (sc[i] == -1) {
				Queue<Integer> vq = new LinkedList<Integer>();
				vq.offer(i);
				sc[i] = i;
				while (!vq.isEmpty()) {
					int curNode = vq.poll();
					for (int j = 0; j < distMatrix.length; j++) {
						if (distMatrix[curNode][j] != null && distMatrix[curNode][j] == maxDist && sc[j] == -1) {
							sc[j] = curNode;
							vq.offer(j);
						}
					}
				}
			}
		}
		return sc;
	}

	private double dist(Instance instance, Instance instance2) {
		double sum = 0;
		for (int i = 0; i < instance.numAttributes(); i++) {
			double a = Double.isNaN(instance.value(i)) ? 0 : instance.value(i);
			double b = Double.isNaN(instance2.value(i)) ? 0 : instance2.value(i);
			sum += Math.pow(a - b, 2.0);
		}
		return Math.sqrt(sum);
	}

	private void runOdin() {
		findOutlierByCC();
		genKNNGraph();
		filterByDegree();
	}

	private void genKNNGraph() {
		adj = new boolean[dataset.size()][dataset.size()];
		for (int i = 0; i < dataset.size(); i++) {
			if (!outlierSet.contains(i)) {
				ArrayIndexComparator<Double> comp = new ArrayIndexComparator<Double>(distMatrix[i]);
				Integer[] index = comp.createIndexArray();
				Arrays.sort(index, comp);
				for (int j = 0; j < k; j++) {
					if (distMatrix[i][index[j]] == null || distMatrix[i][index[j]] == maxDist)
						break;
					adj[index[j]][i] = true;
					System.out.print(distMatrix[i][index[j]] + ",");
				}
				System.out.println();
			}
		}
	}

	private void filterByDegree() {
		for (int i = 0; i < dataset.size(); i++) {
			if (!outlierSet.contains(i)) {
				int curDegree = 0;
				for (int j = 0; j < dataset.size(); j++) {
					if (adj[i][j])
						curDegree++;
				}
				if (curDegree <= indegree) {
					outlierSet.add(i);
				}
			}
		}
	}

	@Override
	public Set<Integer> outliers(Instances dataset) {
		this.dataset = dataset;
		runOdin();
		return outlierSet;
	}

}
