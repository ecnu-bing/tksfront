package ttks.actions;

import java.util.ArrayList;
import java.util.List;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;

import casdb.CassandraConn;
import casdb.TweetDao;
import common.ConfigLoader;
import net.sf.json.JSONObject;
import searchapi.QueryType;
import summarization.EventSummarization;
import summarization.TimeLine;
import summarization.TimeSeries;

@ParentPackage("json")
public class EventSumAction {
	private String keyword;
	private long startTime;
	private long endTime;

	private String timeline;

	public String getTimeline() {
		return timeline;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	@Action(value = "esum", results = { @Result(name = "success", type = "json") })
	public String execQuery() throws Exception {
		CassandraConn conn = new CassandraConn();
		conn.connect(ConfigLoader.props.getProperty("cassdb", "127.0.0.1"));
		EventSummarization sum = new EventSummarization(conn);
		TweetDao tweetDao = new TweetDao(conn);
		List<Long> mids = TemporalKeywordSearch.execQuery(keyword, 40, startTime, endTime, QueryType.WEIGHTED);

		List<JSONObject> objs = new ArrayList<JSONObject>();
		List<TimeSeries> ts = new ArrayList<TimeSeries>();
		for (long cur : mids) {
			// fetch status
			String mid = Long.toString(cur);
			String status = tweetDao.getStatusByMid(mid);
			if (status != null) {
				try {
					JSONObject obj = JSONObject.fromObject(status);
					JSONObject statusReply = new JSONObject();
					statusReply.put("text", obj.getString("text"));
					statusReply.put("uname", obj.getString("uname"));
					statusReply.put("mid", Long.parseLong(obj.getString("mid")));
					statusReply.put("omid", Long.parseLong(obj.getString("omid")));
					objs.add(statusReply);
					List<long[]> sery = tweetDao.queryTimeSeries(mid, startTime, endTime);
					ts.add(new TimeSeries(sery, startTime, endTime));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
		TimeLine tline = sum.genTimeLine(objs, ts, startTime, endTime);
		try {
			timeline = JSONObject.fromObject(tline).toString();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return "success";
	}
}
