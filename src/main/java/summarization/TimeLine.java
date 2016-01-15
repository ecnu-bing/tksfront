package summarization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import net.sf.json.JSONObject;

public class TimeLine {
	private List<Event> events = new ArrayList<Event>();

	public void addEvent(List<JSONObject> statues, List<Entry<String, Integer>> summary, List<Long> timeRange) {
		events.add(new Event(statues, summary, timeRange));
	}

	public List<Event> getEvents() {
		return events;
	}

	public void setEvents(List<Event> events) {
		this.events = events;
	}
}
