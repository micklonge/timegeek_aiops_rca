package aiops.rootcause.data;

import java.util.List;
import java.util.Map;

public class Neo4jData {
	
	private Long id = null;
	
	private List<String> labels = null;
	private Map<String, Map<String, String>> properties = null;
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public Long getId() {
		return id;
	}
	
	public void setLabels(List<String> labels) {
		this.labels = labels;
	}
	
	public List<String> getLabels() {
		return this.labels;
	}
	
	public void setProperties(Map<String, Map<String, String>> properties) {
		this.properties = properties;
	}
	
	public Map<String, Map<String, String>> getProperties() {
		return this.properties;
	}

}
