package ttks.actions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.collections.Factory;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;

import com.opensymphony.xwork2.ActionSupport;

import casdb.CassandraConn;
import casdb.RetweetStatsDao;
import casdb.TweetDao;
import collection.DefaultedPutMap;
import common.ConfigLoader;
import weibo4j.org.json.JSONObject;

public class RetweetStats {
	private static final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

	public static class RetweetStatsAction extends ActionSupport {
		protected String mids;
		protected long startTime;
		protected long endTime;

		public void setMids(String mids) {
			this.mids = mids;
		}

		public void setStartTime(long startTime) {
			this.startTime = startTime;
		}

		public void setEndTime(long endTime) {
			this.endTime = endTime;
		}
	}

	@ParentPackage("json")
	public static class RetweetTimeSeriesStats extends RetweetStatsAction {
		List<String> timeAxis = new ArrayList<String>();
		Map<String, List<Integer>> series = new HashMap<String, List<Integer>>();

		@Action(value = "/rtstats/tseries", results = { @Result(name = "success", type = "json") })
		public String fetchRTStats() throws Exception {
			CassandraConn conn = new CassandraConn();
			try {
				conn.connect(ConfigLoader.props.getProperty("cassdb", "127.0.0.1"));
				TweetDao tweetDao = new TweetDao(conn);
				Map<String, Integer> ts = new TreeMap<String, Integer>();
				Map<String, Integer> tsDec = DefaultedPutMap.decorate(ts, new Factory() {
					@Override
					public Object create() {
						return 0;
					}

				});

				List<JSONObject> objs = new ArrayList<JSONObject>();
				for (String mid : mids.split(",")) {
					List<long[]> sery = tweetDao.queryTimeSeries(mid, startTime, endTime);
					for (long[] point : sery) {
						tsDec.put(format.format(new Date(point[0])), (int) point[1]);
					}
				}
				timeAxis.addAll(ts.keySet());
				series.put("tweets", new ArrayList<Integer>(ts.values()));
			} catch (Exception ex) {
				timeAxis.add("2012-10-10 11:00:00");
				timeAxis.add("2012-10-10 14:00:00");
				timeAxis.add("2012-10-10 15:00:00");
				timeAxis.add("2012-10-10 17:00:00");
				series.put("tweets", Arrays.asList(10, 20, 10, 40));
			} finally {
				conn.close();
			}
			return "success";
		}

		public List<String> getTimeAxis() {
			return timeAxis;
		}

		public Map<String, List<Integer>> getSeries() {
			return series;
		}
	}

	@ParentPackage("json")
	public static class RetweetUserStats extends RetweetStatsAction {

		private Map<String, Integer> genderDist = new HashMap<String, Integer>();
		private Map<Boolean, Integer> vipDist = new HashMap<Boolean, Integer>();
		private Map<String, Integer> clientDist = new HashMap<String, Integer>();

		@Action(value = "/rtstats/user", results = { @Result(name = "success", type = "json") })
		public String fetchRTStats() throws Exception {
			CassandraConn conn = new CassandraConn();
			try {

				conn.connect(ConfigLoader.props.getProperty("cassdb", "127.0.0.1"));
				RetweetStatsDao dao = new RetweetStatsDao(conn);
				for (String mid : mids.split(",")) {
					updateBoolMap(vipDist, dao.getVIPForTweet(mid, startTime, endTime));
					updateMap(genderDist, dao.getGenderForTweet(mid, startTime, endTime));
					updateMap(clientDist, dao.getClientForTweet(mid, startTime, endTime));
				}

			} catch (Exception ex) {
				vipDist.put(true, 100);
				vipDist.put(false, 100);
				genderDist.put("male", 100);
				genderDist.put("female", 10);
				clientDist.put("微博网页", 100);
				clientDist.put("iphone", 10);
			} finally {
				conn.close();
			}
			return "success";
		}

		public Map<Boolean, Integer> getVipDist() {
			return vipDist;
		}

		public Map<String, Integer> getClientDist() {
			return clientDist;
		}

		public Map<String, Integer> getGenderDist() {
			return genderDist;
		}
	}

	@ParentPackage("json")
	public static class RetweetLocStats extends RetweetStatsAction {
		private Map<String, Integer> locDist = new HashMap<String, Integer>();

		@Action(value = "/rtstats/loc", results = { @Result(name = "success", type = "json") })
		public String fetchRTStats() throws Exception {
			CassandraConn conn = new CassandraConn();
			try {

				conn.connect(ConfigLoader.props.getProperty("cassdb", "127.0.0.1"));
				RetweetStatsDao dao = new RetweetStatsDao(conn);
				for (String mid : mids.split(",")) {
					updateMap(locDist, dao.getLocForTweet(mid, startTime, endTime));
				}
			} catch (Exception ex) {
				locDist.put("上海", 100);
				locDist.put("北京", 10);
			} finally {
				conn.close();
			}
			return "success";
		}

		public Map<String, Integer> getLocDist() {
			return locDist;
		}
	}

	@ParentPackage("json")
	public static class RetweetVIPStats extends RetweetStatsAction {

		private Map<Boolean, Integer> vipDist = new HashMap<Boolean, Integer>();

		@Action(value = "/rtstats/vip", results = { @Result(name = "success", type = "json") })
		public String fetchRTStats() throws Exception {
			CassandraConn conn = new CassandraConn();
			try {

				conn.connect(ConfigLoader.props.getProperty("cassdb", "127.0.0.1"));
				RetweetStatsDao dao = new RetweetStatsDao(conn);
				for (String mid : mids.split(",")) {
					updateBoolMap(vipDist, dao.getVIPForTweet(mid, startTime, endTime));
				}

			} catch (Exception ex) {
				vipDist.put(true, 100);
				vipDist.put(false, 100);
			} finally {
				conn.close();
			}
			return "success";
		}

		public Map<Boolean, Integer> getVipDist() {
			return vipDist;
		}

	}

	@ParentPackage("json")
	public static class RetweetGenderStats extends RetweetStatsAction {
		private Map<String, Integer> genderDist = new HashMap<String, Integer>();

		@Action(value = "/rtstats/gender", results = { @Result(name = "success", type = "json") })
		public String fetchRTStats() throws Exception {
			CassandraConn conn = new CassandraConn();
			try {

				conn.connect(ConfigLoader.props.getProperty("cassdb", "127.0.0.1"));
				RetweetStatsDao dao = new RetweetStatsDao(conn);
				for (String mid : mids.split(",")) {
					updateMap(genderDist, dao.getGenderForTweet(mid, startTime, endTime));
				}
			} catch (Exception ex) {
				genderDist.put("male", 100);
				genderDist.put("female", 10);
			} finally {
				conn.close();
			}
			return "success";
		}

		public Map<String, Integer> getGenderDist() {
			return genderDist;
		}

	}

	@ParentPackage("json")
	public static class RetweetMoodStats extends RetweetStatsAction {
		private Map<String, Integer> moodDist = new HashMap<String, Integer>();

		@Action(value = "/rtstats/mood", results = { @Result(name = "success", type = "json") })
		public String fetchRTStats() throws Exception {
			CassandraConn conn = new CassandraConn();
			try {

				conn.connect(ConfigLoader.props.getProperty("cassdb", "127.0.0.1"));
				RetweetStatsDao dao = new RetweetStatsDao(conn);
				for (String mid : mids.split(",")) {
					updateMap(moodDist, dao.getMoodForTweet(mid, startTime, endTime));
				}
			} catch (Exception ex) {
				moodDist.put("快乐", 100);
				moodDist.put("悲伤", 10);
				moodDist.put("愤怒", 10);
				moodDist.put("恐惧", 10);
				moodDist.put("惊奇", 10);
				moodDist.put("厌恶", 10);
			} finally {
				conn.close();
			}
			return "success";
		}

		public Map<String, Integer> getMoodDist() {
			return moodDist;
		}

	}

	@ParentPackage("json")
	public static class RetweetClientStats extends RetweetStatsAction {
		private Map<String, Integer> clientDist = new HashMap<String, Integer>();

		@Action(value = "/rtstats/client", results = { @Result(name = "success", type = "json") })
		public String fetchRTStats() throws Exception {
			CassandraConn conn = new CassandraConn();
			try {

				conn.connect(ConfigLoader.props.getProperty("cassdb", "127.0.0.1"));
				RetweetStatsDao dao = new RetweetStatsDao(conn);
				for (String mid : mids.split(",")) {
					updateMap(clientDist, dao.getClientForTweet(mid, startTime, endTime));
				}
			} catch (Exception ex) {
				clientDist.put("微博网页", 100);
				clientDist.put("iphone", 10);
			} finally {
				conn.close();
			}
			return "success";
		}

		public Map<String, Integer> getClientDist() {
			return clientDist;
		}
	}

	private static void updateMap(Map<String, Integer> map, Map<String, Integer> newMap) {
		for (Entry<String, Integer> entry : newMap.entrySet()) {
			String key = entry.getKey();
			Integer val = entry.getValue();
			if (map.containsKey(key)) {
				map.put(key, map.get(key) + val);
			} else {
				map.put(key, val);
			}
		}
	}

	private static void updateBoolMap(Map<Boolean, Integer> map, Map<Boolean, Integer> newMap) {
		for (Entry<Boolean, Integer> entry : newMap.entrySet()) {
			Boolean key = entry.getKey();
			Integer val = entry.getValue();
			if (map.containsKey(key)) {
				map.put(key, map.get(key) + val);
			} else {
				map.put(key, val);
			}
		}
	}
}
