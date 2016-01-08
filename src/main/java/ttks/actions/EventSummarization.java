package ttks.actions;

import java.util.List;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Result;

import casdb.CassandraConn;
import casdb.WordDao;

public class EventSummarization {
	private String eventID;
	private long startTime;
	private long endTime;

	private List<String> words;
	private List<Float> weight;

	@Action(value = "tweets", results = { @Result(name = "success", type = "json") })
	public String execQuery() throws Exception {
		CassandraConn conn = new CassandraConn();
		conn.connect("10.11.1.212");
		WordDao wordDaoafka = new WordDao(conn);

		return "success";
	}
}
