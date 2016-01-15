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
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import casdb.CassandraConn;
import casdb.TweetDao;
import casdb.WordDao;
import collection.DefaultedPutMap;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import shingle.TextShingle;
import util.DateUtil;
import weka.clusterers.AbstractClusterer;
import weka.clusterers.EM;
import weka.clusterers.HierarchicalClusterer;
import weka.core.Attribute;
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
 * @author xiafan
 *
 */
public class EventSummarization {
	private final static int EVENT_NUM = 10;
	TweetDao dao;
	WordDao wordDao;
	TextShingle shingle = new TextShingle(null);
	long startTime;
	long endTime;
	HashMap<String, TimeSeries> term2ts = new HashMap<String, TimeSeries>();
	List<JSONObject> statuses;
	List<TimeSeries> ts;
	String clusterAlg = "EM";// "EM","hie"

	List<Set<String>> features;
	List<Integer>[] cluster2Microblogs;
	int[] clusterDist;

	public EventSummarization(CassandraConn conn) {
		dao = new TweetDao(conn);
		wordDao = new WordDao(conn);
	}

	public TimeLine genTimeLine(List<JSONObject> statuses, List<TimeSeries> ts, long startTime, long endTime) {
		this.statuses = statuses;
		this.ts = ts;
		this.startTime = startTime;
		this.endTime = endTime;
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
					eStatues.add(statuses.get(sIdx));
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

				List<Long> timeRange = genEventRange(cluster2Microblogs[i], ts);
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

	private List<Long> genEventRange(List<Integer> mids, List<TimeSeries> ts) {
		TimeSeries overall = new TimeSeries();
		for (int idx : mids) {
			overall.merge(ts.get(idx));
		}
		return Arrays.asList(TimeSeries.outlineRange(overall));
	}

	/**
	 * 基于内容聚类？是否考虑时间，如何考虑
	 */
	private void clusterMicroblogs(Instances dataset) {
		AbstractClusterer cluster = null;
		if (clusterAlg.equals("EM")) {
			cluster = new EM();
			try {
				String[] options = new String[2];
				options[0] = "-I"; // max. iterations
				options[1] = "40";
				//options[2] = "-N";
				//options[3] = "10";
				cluster.setOptions(options);
				cluster.buildClusterer(dataset);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (clusterAlg.equals("hie")) {
			cluster = new HierarchicalClusterer();
			try {
				String[] options = new String[4];
				options[0] = "-L"; // max. iterations
				options[1] = "Average";
				options[2] = "-N";
				options[3] = "3";
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

	private Map<String, Integer> wordDist() {
		Map<String, Integer> ret = DefaultedPutMap.decorate(new HashMap<String, Integer>(), new Factory() {
			@Override
			public Object create() {
				return 0;
			}
		});

		for (int i = 0; i < statuses.size(); i++) {
			JSONObject curStatus = statuses.get(i);
			try {
				List<String> terms = shingle.shingling(curStatus.getString("text"));
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
		for (int i = 0; i < statuses.size(); i++) {
			JSONObject curStatus = statuses.get(i);
			try {
				List<String> terms = shingle.shingling(curStatus.getString("text"));
				Set<String> feature = new HashSet<String>();
				for (String term : terms) {
					if (wordDist.get(term) > 2) {
						feature.add(term);
						termVocab.add(term);
					} else {
						float prob = tsSimilarity(ts.get(i), getTermFreqSeries(term));
						if (prob > 0.2f) {
							feature.add(term);
							termVocab.add(term);
						}
					}
				}
				features.add(feature);
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
		return dataset;
	}

	/**
	 * 计算两个时间序列的相似度
	 * 
	 * @param a
	 * @param b
	 * @return
	 */
	private float tsSimilarity(TimeSeries a, TimeSeries b) {
		float ret = 0;
		PearsonsCorrelation cor = new PearsonsCorrelation();
		ret = (float) cor.correlation(a.toArray(), b.toArray());
		// (float) (1.0f - GrangerTest.granger(b.toArray(), a.toArray(), 1))
		return ret;

		/*
		 * // 可以用的方法：关联系数，granger causality test List<Long> interval =
		 * detectEventInterval(a); return outlineProb(b, interval.get(0),
		 * interval.get(1));
		 */
	}

	/**
	 * 查询每个单词对应的词频
	 * 
	 * @param term
	 * @return
	 */
	private TimeSeries getTermFreqSeries(String term) {
		if (!term2ts.containsKey(term)) {
			term2ts.put(term, new TimeSeries(wordDao.getWordFreq(term, startTime, endTime), startTime, endTime));
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

	private float outlineProb(TimeSeries ts, long start, long end) {
		long compStart = 2 * start - end;
		long compEnd = 2 * end + start;

		float sum = 0;
		float sqSum = 0;
		long curTime = compStart;
		while (curTime <= compEnd) {
			float val = ts.getValueAt(curTime);
			sum += val;
			sqSum += val * val;
		}
		int numOfPoint = (int) ((compEnd - compStart) / DateUtil.HOUR_GRANU);
		float exp = sum / numOfPoint;
		float var = (float) (sqSum / numOfPoint - Math.pow(exp, 2));

		float count = 0;
		curTime = compStart;
		while (curTime <= end) {
			float val = ts.getValueAt(curTime);
			if (val - exp > var) {
				count++;
			}
		}
		return count / ((end - start) / DateUtil.HOUR_GRANU);
	}
}
