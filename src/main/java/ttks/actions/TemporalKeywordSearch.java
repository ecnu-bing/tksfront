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

import searchapi.QueryType;
import searchapi.TKeywordQuery;
import searchapi.TweetService;
import searchapi.TweetService.Client;
import xiafan.util.DateUtil;

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
		try {
			mids = new ArrayList<Long>();
			// mids.add(1l);
			TSocket transport = new TSocket("127.0.0.1", 10000);
			TProtocol protocol = new TBinaryProtocol(transport);
			Client client = new TweetService.Client(protocol);
			transport.open();
			if (queryType == null)
				queryType = QueryType.WEIGHTED.toString();
			else
				queryType = queryType.toUpperCase();
			mids = client.search(new TKeywordQuery(keyword, topk, DateUtil.diffByWeiboStartTime(start),
					DateUtil.diffByWeiboStartTime(end), QueryType.valueOf(queryType)));
			transport.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return "success";
	}

}
