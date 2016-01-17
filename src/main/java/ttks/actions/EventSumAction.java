package ttks.actions;

import java.util.ArrayList;
import java.util.List;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;

import casdb.CassandraConn;
import casdb.TweetDao;
import common.ConfigLoader;
import dase.timeseries.structure.ITimeSeries;
import dase.timeseries.structure.SparseTimeSeries;
import net.sf.json.JSONObject;
import searchapi.QueryType;
import summarization.EventSummarization;
import summarization.EventSummarization.SumContext;
import summarization.TimeLine;
import util.DateUtil;

@ParentPackage("json")
public class EventSumAction {
	private String keyword;
	private int topk;
	private long startTime;
	private long endTime;
	private boolean shouldStandard;
	private String clusterAlg = "EM";
	private int sumNum = 10;
	private double simThreshold = 0.0;
	private String simType = "";

	private String timeline;

	public String getTimeline() {
		return timeline;
	}

	public void setSimThreshold(double simThreshold) {
		this.simThreshold = simThreshold;
	}

	public void setTopk(int topk) {
		this.topk = topk;
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

	public void setShouldStandard(boolean shouldStandard) {
		this.shouldStandard = shouldStandard;
	}

	public void setClusterAlg(String clusterAlg) {
		this.clusterAlg = clusterAlg;
	}

	public void setSumNum(int sumNum) {
		this.sumNum = sumNum;
	}

	public void setSimType(String simType) {
		this.simType = simType;
	}

	@Action(value = "esum", results = { @Result(name = "success", type = "json") })
	public String execQuery() throws Exception {
		CassandraConn conn = new CassandraConn();
		conn.connect(ConfigLoader.props.getProperty("cassdb", "127.0.0.1"));
		SumContext context = new SumContext();
		context.clusterAlg = clusterAlg;
		context.startTime = startTime;
		context.endTime = endTime;
		context.sumNum = sumNum;
		context.shouldStandard = shouldStandard;
		context.simType = simType;
		context.simThreshold = simThreshold;
		EventSummarization sum = new EventSummarization(context, conn);

		TweetDao tweetDao = new TweetDao(conn);
		List<Long> mids = TemporalKeywordSearch.execQuery(keyword, topk, startTime, endTime, QueryType.WEIGHTED);

		List<JSONObject> objs = new ArrayList<JSONObject>();
		List<ITimeSeries> ts = new ArrayList<ITimeSeries>();
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
					ts.add(new SparseTimeSeries(sery, DateUtil.HOUR_GRANU, startTime, endTime));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
		context.statuses = objs;
		context.ts = ts;
		TimeLine tline = sum.genTimeLine();
		try {
			timeline = JSONObject.fromObject(tline).toString();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return "success";
	}
}
