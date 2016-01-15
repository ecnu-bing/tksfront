package ttks.actions;

import org.apache.struts2.convention.annotation.ParentPackage;

import com.opensymphony.xwork2.ActionSupport;

@ParentPackage("json")
public class WordAction extends ActionSupport {
	String words;

	public void setWords(String words) {
		this.words = words;
	}
	
	
}
