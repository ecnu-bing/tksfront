package ttks.actions;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xiafan
 */
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;

import com.opensymphony.xwork2.ActionSupport;

import common.ConfigLoader;
import searchapi.QueryType;
import searchapi.TKeywordQuery;
import searchapi.TweetService;
import searchapi.TweetService.Client;
import util.DateUtil;

@ParentPackage("json")
public class TemporalKeywordSearch extends ActionSupport {
	String keyword;
	int topk;
	long start;
	long end;
	String queryType;

	List<Long> mids;

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}

	public void setTopk(int k) {
		this.topk = k;
	}

	public void setStart(long start) {
		this.start = start;
	}

	public void setEnd(long end) {
		this.end = end;
	}

	public void setQueryType(String queryType) {
		this.queryType = queryType;
	}

	public List<Long> getMids() {
		return mids;
	}

	@Action(value = "ttks", results = { @Result(name = "success", type = "json") })
	public String execQuery() throws Exception {
		mids = execQuery(keyword, topk, start, end, QueryType.valueOf(queryType.toUpperCase()));
		return "success";
	}

	public static List<Long> execQuery(String keyword, int topk, long start, long end, QueryType queryType) {
		List<Long> mids = new ArrayList<Long>();
		try {

			// mids.add(1l);
			TSocket transport = new TSocket(ConfigLoader.props.getProperty("indexServer", "127.0.0.1"),
					Short.parseShort(ConfigLoader.props.getProperty("indexPort", "10000")));
			TProtocol protocol = new TBinaryProtocol(transport);
			Client client = new TweetService.Client(protocol);
			transport.open();
			mids = client.search(new TKeywordQuery(keyword, topk, DateUtil.diffByWeiboStartTime(start),
					DateUtil.diffByWeiboStartTime(end), queryType));
			transport.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return mids;
	}

}
