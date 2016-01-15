package summarization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.json.JSONObject;


public class Event {
	long startTime;
	long endTime;
	Map<String, Integer> words = new HashMap<String, Integer>();
	List<String> statuses = new ArrayList<String>();

	public Event(List<JSONObject> statuses, List<Entry<String, Integer>> summary, List<Long> timeRange) {
		for (Entry<String, Integer> sum : summary) {
			words.put(sum.getKey(), sum.getValue());
		}
		startTime = timeRange.get(0);
		endTime = timeRange.get(1);
		for (JSONObject stat : statuses) {
			this.statuses.add(stat.toString());
		}
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public long getendTime() {
		return endTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public Map<String, Integer> getWords() {
		return words;
	}

	public void setWords(Map<String, Integer> words) {
		this.words = words;
	}

	public List<String> getStatuses() {
		return statuses;
	}

	public void setStatuses(List<String> statuses) {
		this.statuses = statuses;
	}
}
