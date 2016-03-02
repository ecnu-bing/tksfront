package ttks.actions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;

import com.opensymphony.xwork2.ActionSupport;

import casdb.CassandraConn;
import casdb.TweetDao;
import common.ConfigLoader;
import dase.timeseries.structure.SparseTimeSeries;
import summarization.ClusterSummarization;
import util.DateUtil;
import weibo4j.org.json.JSONObject;

@ParentPackage("json")
public class FetchTweetsAction extends ActionSupport {
	private static final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
	String mids;
	Map<String, String> tweets = new HashMap<String, String>();
	List<String> timeAxis = new ArrayList<String>();
	Map<String, List<Integer>> series = new HashMap<String, List<Integer>>();
	private long startTime;
	private long endTime;

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public void setMids(String mids) {
		this.mids = mids;
	}

	public Map<String, String> getTweets() {
		return tweets;
	}

	public List<String> getTimeAxis() {
		return timeAxis;
	}

	public Map<String, List<Integer>> getSeries() {
		return series;
	}

	@Action(value = "tweets", results = { @Result(name = "success", type = "json") })
	public String execQuery() throws Exception {
		CassandraConn conn = new CassandraConn();
		conn.connect(ConfigLoader.props.getProperty("cassdb", "127.0.0.1"));
		TweetDao tweetDao = new TweetDao(conn);
		TreeSet<Long> axis = new TreeSet<Long>();
		Map<String, List<long[]>> curSeries = new HashMap<String, List<long[]>>();
		// axis.headSet(toElement)
		try {
			List<JSONObject> objs = new ArrayList<JSONObject>();
			for (String mid : mids.split(",")) {
				// fetch status
				String status = tweetDao.getStatusByMid(mid);

				if (status != null) {
					String statusStr = "";
					try {
						JSONObject obj = new JSONObject(status);
						JSONObject statusReply = new JSONObject();
						statusReply.put("text", obj.getString("text"));
						statusReply.put("uname", obj.getString("uname"));
						statusReply.put("mid", obj.getString("mid"));
						objs.add(statusReply);
						statusStr = statusReply.toString();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
					tweets.put(mid, statusStr);
				} else
					tweets.put(mid, "{text:'sorry, the content of the tweet is not fetched', uname:'haha'}");
				// fetch time series
				List<long[]> sery = tweetDao.queryTimeSeries(mid, startTime, endTime);
				curSeries.put(mid, sery);
				for (long[] point : sery) {
					axis.add(point[0]);
				}
			}
			List<SparseTimeSeries> ts = new ArrayList<SparseTimeSeries>();
			endTime = DateUtil.roundByHour(endTime);
			for (JSONObject obj : objs) {
				ts.add(new SparseTimeSeries(curSeries.get(obj.getString("mid")), DateUtil.HOUR_GRANU, startTime,
						endTime));
			}
			// sum.genTimeLine(objs, ts, startTime, endTime);

			for (Entry<String, List<long[]>> entry : curSeries.entrySet()) {
				List<Integer> list = new ArrayList<Integer>(axis.size());
				for (int i = 0; i < axis.size(); i++)
					list.add(0);
				series.put(entry.getKey(), list);
				for (long[] point : entry.getValue()) {
					list.set(axis.headSet(point[0]).size(), (int) point[1]);
				}
			}
			for (long timePoint : axis) {
				timeAxis.add(format.format(new Date(timePoint)));
			}
		} finally {
			conn.close();
		}
		return "success";
	}

}
