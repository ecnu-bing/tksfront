package summarization;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections.Factory;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import casdb.CassandraConn;
import casdb.TweetDao;
import casdb.WordDao;
import collection.DefaultedPutMap;
import dase.timeseries.analysis.Similarity;
import dase.timeseries.structure.ITimeSeries;
import dase.timeseries.structure.SparseTimeSeries;
import dase.timeseries.structure.Standarization;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import shingle.ITextShingle;
import shingle.ShingleFactory;
import summarization.outlier.ODinOutlierDetector;
import util.DateUtil;
import weka.clusterers.AbstractClusterer;
import weka.clusterers.EM;
import weka.clusterers.HierarchicalClusterer;
import weka.clusterers.SimpleKMeans;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SparseInstance;

/**
 * 基于什么思想，有些什么特征可以使用， 每条微博：内容可以分词（每个词可以知道它们的词频时序），每条微博也有一个转发的时间序列
 * 
 * 做事件摘要：通过微博关键词检索出top100的热门微博 1.
 * 对于每条微博，分词，获取每个词的时间序列。判断在微博的热门期间，相应的单词的是否也是异常热门的，选出这些单词作为备选
 * 
 * step 1: 基于词频时序，为每条微博选出热门的词频
 * 
 * 
 * 
 * 到底什么算一个事件？有可能有些微博仅仅只是一些小的讨论
 * 
 * @author xiafan
 *
 */
public class ClusterSummarization implements IEventSummarization {
	private static final Logger logger = Logger.getLogger(ClusterSummarization.class);
	SumContext context;

	TweetDao dao;
	WordDao wordDao;
	ITextShingle shingle = ShingleFactory.createShingle();

	List<Set<String>> features;
	List<Integer>[] cluster2Microblogs;
	int[] clusterDist;
	public HashMap<String, ITimeSeries> term2ts = new HashMap<String, ITimeSeries>();

	public ClusterSummarization(SumContext context, CassandraConn conn) {
		this.context = context;
		dao = new TweetDao(conn);
		wordDao = new WordDao(conn);
	}

	@Override
	public TimeLine genTimeLine() {
		List<ITimeSeries> oSer = context.ts;
		if (context.shouldStandard) {
			oSer = standard();
		}

		Instances dataset = genWekaData();
		clusterMicroblogs(dataset);
		// 生成timeline，确定每个时间点使用些什么对象？
		TimeLine timeline = new TimeLine();
		System.out.println(Arrays.toString(clusterDist));
		for (int i = 0; i < clusterDist.length; i++) {
			if (clusterDist[i] >= 1) {
				List<JSONObject> eStatues = new ArrayList<JSONObject>();
				Map<String, Integer> sum = DefaultedPutMap.decorate(new HashMap<String, Integer>(), new Factory() {
					@Override
					public Object create() {
						return 1;
					}
				});

				for (int sIdx : cluster2Microblogs[i]) {
					eStatues.add(context.statuses.get(sIdx));
					for (String term : features.get(sIdx)) {
						sum.put(term, sum.get(term) + 1);
					}
				}
				ArrayList<Entry<String, Integer>> sumList = new ArrayList<>(sum.entrySet());
				Collections.sort(sumList, new Comparator<Entry<String, Integer>>() {
					@Override
					public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
						return 0 - o1.getValue().compareTo(o2.getValue());
					}
				});

				List<Long> timeRange = genEventRange(cluster2Microblogs[i], oSer);
				List<Entry<String, Integer>> sumWords = new ArrayList<Entry<String, Integer>>();
				for (Entry<String, Integer> entry : sumList.subList(0, Math.min(30, sumList.size()))) {
					sumWords.add(entry);
				}
				timeline.addEvent(eStatues, sumWords, timeRange);
			}
		}

		System.out.println("gen timeline complete");
		return timeline;
	}

	private List<ITimeSeries> standard() {
		List<ITimeSeries> ret = new ArrayList<ITimeSeries>();
		List<ITimeSeries> series = new ArrayList<ITimeSeries>();
		for (ITimeSeries curTs : context.ts) {
			series.add(Standarization.zscore(curTs));
		}
		ret = context.ts;
		context.ts = series;
		return ret;
	}

	private List<Long> genEventRange(List<Integer> mids, List<ITimeSeries> ts) {
		ITimeSeries overall = ITimeSeries.merge(ts);
		for (int idx : mids) {
			overall.merge(ts.get(idx));
		}
		return Arrays.asList(SparseTimeSeries.outlineRange(overall));
	}

	/**
	 * 基于内容聚类？是否考虑时间，如何考虑
	 */
	private void clusterMicroblogs(Instances dataset) {
		if (context.clusterAlg.equals("kmeans_outlier")) {
			KMedoidCluster cluster = new KMedoidCluster();
			if (context.sumNum > 0)
				cluster.setK(context.sumNum);
			cluster.cluster(dataset);

			cluster2Microblogs = new ArrayList[cluster.numberOfClusters()];
			for (int i = 0; i < cluster2Microblogs.length; i++) {
				cluster2Microblogs[i] = new ArrayList<Integer>();
			}
			clusterDist = new int[cluster.numberOfClusters()];
			for (Entry<Integer, Integer> entry : cluster.getPoint2Cluster().entrySet()) {
				cluster2Microblogs[entry.getValue()].add(entry.getKey());
				clusterDist[entry.getValue()]++;
			}
			return;
		}

		AbstractClusterer cluster = null;
		if (context.clusterAlg.equals("EM")) {
			cluster = new EM();
			try {
				String[] options = new String[2];
				if (context.sumNum > 0) {
					options = new String[4];
					options[0] = "-I"; // max. iterations
					options[1] = "40";
					options[2] = "-N";
					options[3] = Integer.toString(context.sumNum);
				} else {
					options = new String[2];
					options[0] = "-I"; // max. iterations
					options[1] = "40";
				}
				cluster.setOptions(options);
				cluster.buildClusterer(dataset);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (context.clusterAlg.equals("hie")) {
			cluster = new HierarchicalClusterer();
			try {
				String[] options = new String[4];
				options[0] = "-L"; // max. iterations
				options[1] = "Average";
				options[2] = "-N";
				options[3] = Integer.toString(context.sumNum);
				cluster.setOptions(options);
				cluster.buildClusterer(dataset);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (context.clusterAlg.equals("kmeans")) {
			cluster = new SimpleKMeans();
			try {
				String[] options = new String[2];
				options[0] = "-N";
				options[1] = Integer.toString(context.sumNum);
				cluster.setOptions(options);
				cluster.buildClusterer(dataset);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			cluster2Microblogs = new ArrayList[cluster.numberOfClusters()];
			for (int i = 0; i < cluster2Microblogs.length; i++) {
				cluster2Microblogs[i] = new ArrayList<Integer>();
			}
			clusterDist = new int[cluster.numberOfClusters()];
			for (int i = 0; i < dataset.size(); i++) {
				int idx = cluster.clusterInstance(dataset.instance(i));
				cluster2Microblogs[idx].add(i);
				clusterDist[idx]++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static class ZScoreBasedOutlierDetector {
		ArrayList[] cluster2Microblogs;
		Instances dataset;

		public ZScoreBasedOutlierDetector(ArrayList[] cluster2Microblogs, Instances dataset) {
			this.cluster2Microblogs = cluster2Microblogs;
			this.dataset = dataset;
		}

		public void exec() {
			for (ArrayList cluster : cluster2Microblogs) {
				SparseInstance instance = new SparseInstance(dataset.numAttributes());
				for (int idx : (ArrayList<Integer>) cluster) {
					Instance cur = dataset.get(idx);
					for (int i = 0; i < cur.numAttributes(); i++) {
						instance.setValue(i, instance.value(i) + cur.value(i));
					}
				}
				for (int i = 0; i < instance.numAttributes(); i++) {
					instance.setValue(i, instance.value(i) / cluster.size());
				}

			}
		}
	}

	/**
	 * 基于kmeans的原理，过滤异常点。 计算每个聚类中的点到中心点的距离，对于z-score超过2的点就应该从中提出。
	 * 
	 * @param cluster
	 */
	private void detectOutliers(AbstractClusterer cluster) {
		// cluster.
	}

	private Map<String, Integer> wordDist() {
		Map<String, Integer> ret = DefaultedPutMap.decorate(new HashMap<String, Integer>(), new Factory() {
			@Override
			public Object create() {
				return 0;
			}
		});

		for (int i = 0; i < context.statuses.size(); i++) {
			JSONObject curStatus = context.statuses.get(i);
			try {
				List<String> terms = shingle.shingling(curStatus.getString("text"), false);
				for (String term : terms) {
					ret.put(term, ret.get(term) + 1);
				}
			} catch (IOException | JSONException e) {
				e.printStackTrace();
			}
		}
		return ret;
	}

	/**
	 * 将微博数据转换成weka的数据结构
	 * 
	 * @return
	 */
	private Instances genWekaData() {
		features = new ArrayList<Set<String>>();
		Set<String> termVocab = new HashSet<String>();// 词的集合
		Map<String, Integer> wordDist = wordDist();
		for (int i = 0; i < context.statuses.size(); i++) {
			JSONObject curStatus = context.statuses.get(i);
			try {
				List<String> terms = shingle.shingling(curStatus.getString("text"), false);
				Set<String> feature = new HashSet<String>();
				for (String term : terms) {
					if (wordDist.get(term) > 2) {
						feature.add(term);
						termVocab.add(term);
					} else {
						double prob = Similarity.getSim(context.simType).sim(context.ts.get(i),
								getTermFreqSeries(term));
						if (prob > context.simThreshold) {
							feature.add(term);
							termVocab.add(term);
						}
					}
				}
				features.add(feature);
				logger.info(curStatus.getString("text") + "\n" + StringUtils.join(feature, ","));
			} catch (IOException | JSONException e) {
				e.printStackTrace();
			}
		}

		ArrayList<Attribute> attrs = new ArrayList<Attribute>();
		HashMap<String, Attribute> term2Attr = new HashMap<String, Attribute>();
		for (String term : termVocab) {
			Attribute attr = new Attribute(term);
			attrs.add(attr);
			term2Attr.put(term, attr);
		}
		Instances dataset = new Instances("microblogs", attrs, 0);
		for (int i = 0; i < features.size(); i++) {
			Set<String> microblogs = features.get(i);
			SparseInstance instance = new SparseInstance(microblogs.size());
			for (String term : microblogs) {
				instance.setValue(term2Attr.get(term), 1);
			}
			dataset.add(instance);
		}
		ODinOutlierDetector detector = new ODinOutlierDetector();
		Set<Integer> outliers = detector.outliers(dataset);
		Instances pre = dataset;
		dataset = new Instances("microblogs", attrs, 0);
		for (int i = 0; i < pre.size(); i++) {
			if (!outliers.contains(i))
				dataset.add(pre.get(i));
			else {
				logger.info(context.statuses.get(i));
				System.out.println(context.statuses.get(i));
			}
		}
		return dataset;
	}

	/**
	 * 查询每个单词对应的词频
	 * 
	 * @param term
	 * @return
	 */
	private ITimeSeries getTermFreqSeries(String term) {
		if (!term2ts.containsKey(term)) {
			ITimeSeries ts = new SparseTimeSeries(wordDao.getWordFreq(term, context.startTime, context.endTime),
					DateUtil.HOUR_GRANU, context.startTime, context.endTime);
			if (context.shouldStandard) {
				ts = Standarization.zscore(ts);
			}
			term2ts.put(term, ts);
		}
		return term2ts.get(term);
	}

	/**
	 * 判断ts在时间窗口内的异常范围
	 * 
	 * @param ts
	 * @param start
	 * @param end
	 * @return
	 */

	private float outlineProb(SparseTimeSeries ts, long start, long end) {
		long compStart = 2 * start - end;
		long compEnd = 2 * end + start;

		float sum = 0;
		float sqSum = 0;
		long curTime = compStart;
		while (curTime <= compEnd) {
			double val = ts.getValueAt(curTime);
			sum += val;
			sqSum += val * val;
		}
		int numOfPoint = (int) ((compEnd - compStart) / DateUtil.HOUR_GRANU);
		float exp = sum / numOfPoint;
		float var = (float) (sqSum / numOfPoint - Math.pow(exp, 2));

		float count = 0;
		curTime = compStart;
		while (curTime <= end) {
			double val = ts.getValueAt(curTime);
			if (val - exp > var) {
				count++;
			}
		}
		return count / ((end - start) / DateUtil.HOUR_GRANU);
	}

	public static class SumContext {
		public double simThreshold;
		public long startTime;
		public long endTime;
		public List<JSONObject> statuses;
		public List<ITimeSeries> ts;

		// 聚类数
		public int sumNum;

		// 方法相关参数
		public String clusterAlg = "EM";// "EM","hie"
		public String simType = "";
		public boolean shouldStandard;
	}
}
