package aiops.rootcause.main;

public class AlarmInfo {
	
	String error = null;
	
	private NodeEnum nodeEnum = null;
	private String name = null;
	
	private String app = null;
	
	public AlarmInfo(NodeEnum nodeEnum, String name, String app) {
		this.nodeEnum = nodeEnum;
		this.name = name;
		this.app = app;
	}
	
	public AlarmInfo(NodeEnum nodeEnum, String name) {
		this.nodeEnum = nodeEnum;
		this.name = name;
	}
	
	public AlarmInfo(String error) {
		this.error = error;
	}
	
	public void setNodeEnum(NodeEnum nodeEnum) {
		this.nodeEnum = nodeEnum;
	}
	
	public NodeEnum getNodeEnum() {
		return nodeEnum;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public void setApp(String app) {
		this.app = app;
	}
	
	public String getApp() {
		return app;
	}

}
