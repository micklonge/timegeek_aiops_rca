package aiops.rootcause.main;

import java.util.*;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import aiops.rootcause.data.Neo4jData;

public class InferenceEngine {
	
	private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
	
	private static Map<String, InferenceCell> cellFactory = new HashMap<String, InferenceCell>();
	static {
		cellFactory.put(NodeEnum.Application.name(), new InferenceCellApplication());
		cellFactory.put(NodeEnum.Server.name(), new InferenceCell());
		cellFactory.put(NodeEnum.DataCenter.name(), new InferenceCell());
		cellFactory.put(NodeEnum.BaseStation.name(), new InferenceCell());
		cellFactory.put(NodeEnum.Task.name(), new InferenceCellTask());
	}
	
	public static List<InferenceCell> inference(Session session, List<AlarmInfo> alarms) throws Exception {
		Map<String, InferenceCell> cellsMap = new HashMap<String, InferenceCell>();
		
		// 第一步：获取跟告警对象相关的实体信息。
		for (AlarmInfo alarm : alarms) {
			InferenceCell cell = cellFactory.get(alarm.getNodeEnum().name()).generateInferenceCell(alarm);
			cellsMap.put(cell.getInstance(), cell);
		}
		
		boolean isContinue = true;
		while (isContinue) {
			isContinue = false;
			
			for (Map.Entry<String, InferenceCell> entry : cellsMap.entrySet()) {
				if (entry.getValue().getVisited() == true) {
					continue;
				}
				
				isContinue = true;
				entry.getValue().setVisited(true);
				
				// 第二步：针对指定告警，获取跟告警对象对应的实体相关联的实体。
				List<InferenceCell> cells = 
						cellFactory.get(entry.getValue().getNodeEnum().name()).getRelationInferenceCell(session, entry.getValue());
				if (cells.size() <= 0) {
					continue;
				}
				
				// 第三步：针对指定告警，获取跟告警事件相关联的事件。
				List<InferenceCell> events =
						cellFactory.get(entry.getValue().getNodeEnum().name()).getEffectInferenceCell(session, entry.getValue());
				if (events.size() <= 0) {
					continue;
				}
				
				// 第四步：针对指定告警，关联受该告警影响的所有告警。
				for (InferenceCell cell : cells) {
					InferenceCell eCell = cellsMap.get(cell.getInstance());
					
					if (eCell == null) {
						continue;
					}
					
					if (cellFactory.get(entry.getValue().getNodeEnum().name()).isRootCause(eCell, events) == true) {
						eCell.incIndegree();
						entry.getValue().getFaultList().add(eCell);
					}
				}
			}
		}
		
		// 第五步：故障树生成。
		List<InferenceCell> cells = new ArrayList<InferenceCell>();
		for (Map.Entry<String, InferenceCell> entry : cellsMap.entrySet()) {
			if (entry.getValue().getIndegree() <= 0) {
				cells.add(entry.getValue());
			}
		}
		
		return cells;
	}

	/////////////////////////////////////////////////////////////////////////

	// Server
	public static class InferenceCell {
		private NodeEnum nodeEnum = null;
		
		private String instance = null;
		
		private String name = null;
		
		private List<InferenceCell> faultList = new ArrayList<InferenceCell>();
		
		private boolean visited = false;
		private int inDegree = 0;
		
		public void setNodeEnum(NodeEnum nodeEnum) {
			this.nodeEnum = nodeEnum;
		}
		
		public NodeEnum getNodeEnum() {
			return this.nodeEnum;
		}
		
		public void setInstance(String instance) {
			this.instance = instance;
		}
		
		public String getInstance() {
			return this.instance;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getName() {
			return this.name;
		}
		
		public List<InferenceCell> getFaultList() {
			return this.faultList;
		}
		
		public void setVisited(boolean visited) {
			this.visited = visited;
		}
		
		public boolean getVisited() {
			return this.visited;
		}
		
		public void incIndegree() {
			++inDegree;
		}
		
		public int getIndegree() {
			return inDegree;
		}
		
		public InferenceCell generateInferenceCell(AlarmInfo alarm) {
			InferenceCell cell = new InferenceCell();
			
			cell.setNodeEnum(alarm.getNodeEnum());
			cell.setName(alarm.getName());
			
			cell.setInstance(alarm.getNodeEnum().name() + "-" + alarm.getName());
			
			return cell;
		}
		
		public InferenceCell generateInferenceCell(Neo4jData data, boolean isEvent) {
			InferenceCell cell = new InferenceCell();
			
			if (isEvent == true) {
				cell.setNodeEnum(NodeEnum.valueOf(data.getProperties().get("nodeEnum").get("val")));
			} else {
				cell.setNodeEnum(NodeEnum.valueOf(data.getLabels().get(0)));
				cell.setName(data.getProperties().get("name").get("val"));
				cell.setInstance(cell.getNodeEnum().name() + "-" + cell.getName());
			}
			
			return cell;
		}
		
		public List<InferenceCell> getRelationInferenceCell(Session session, InferenceCell cell) {
			List<InferenceCell> cells = new ArrayList<InferenceCell>();
			
			Result result = session.run( "MATCH (n:" + cell.getNodeEnum().name() + " {name: $name}) - [*1] -> (node) RETURN node",
                    Values.parameters("name", cell.getName()));
			while (result.hasNext()) {
				Record record = result.next();
				
				String gstr = gson.toJson(record.get("node").asObject());
				Neo4jData data = gson.fromJson(gstr, Neo4jData.class);
				cells.add(cellFactory.get(data.getLabels().get(0)).generateInferenceCell(data, false));
			}
			
			result = session.run( "MATCH (n:" + cell.getNodeEnum().name() + " {name: $name}) <- [*1] - (node) RETURN node",
                    Values.parameters("name", cell.getName()));
			while (result.hasNext()) {
				Record record = result.next();
				
				String gstr = gson.toJson(record.get("node").asObject());
				Neo4jData data = gson.fromJson(gstr, Neo4jData.class);
				cells.add(cellFactory.get(data.getLabels().get(0)).generateInferenceCell(data, false));
			}
			
			return cells;
		}
		
		public List<InferenceCell> getEffectInferenceCell(Session session, InferenceCell cell) {
			List<InferenceCell> cells = new ArrayList<InferenceCell>();
			
			Result result = session.run( "MATCH (n: Event {nodeEnum: $nodeEnum}) - [*1] -> (node) RETURN node",
                    Values.parameters("nodeEnum", cell.getNodeEnum().name(), "name", cell.getName()));
			while (result.hasNext()) {
				Record record = result.next();
				
				String gstr = gson.toJson(record.get("node").asObject());
				Neo4jData data = gson.fromJson(gstr, Neo4jData.class);
				cells.add(cellFactory.get(data.getProperties().get("nodeEnum").get("val")).generateInferenceCell(data, true));
			}
			
			return cells;
		}
		
		public boolean isRootCause(InferenceCell eNode, List<InferenceCell> events) {
			switch (eNode.getNodeEnum()) {
			case Application:
				for (InferenceCell event : events) {
					if (event.getNodeEnum().equals(NodeEnum.Application) == true) {
						return true;
					}
				}
				break;
			default:
				return false;
			}
			
			return false;
		}
	}
	
	public static class InferenceCellTask extends InferenceCell {
		
		public List<InferenceCell> getEffectInferenceCell(Session session, InferenceCell cell) {
			List<InferenceCell> cells = new ArrayList<InferenceCell>();
			
			Result result = session.run( "MATCH (n: Event {nodeEnum: $nodeEnum, name: $name}) - [*1] -> (node) RETURN node",
                    Values.parameters("nodeEnum", cell.getNodeEnum().name(), "name", cell.getName()));
			while (result.hasNext()) {
				Record record = result.next();
				
				String gstr = gson.toJson(record.get("node").asObject());
				Neo4jData data = gson.fromJson(gstr, Neo4jData.class);
				cells.add(cellFactory.get(data.getProperties().get("nodeEnum").get("val")).generateInferenceCell(data, true));
			}
			
			return cells;
		}
		
		public boolean isRootCause(InferenceCell eNode, List<InferenceCell> events) {
			switch (eNode.getNodeEnum()) {
			case Task:
				for (InferenceCell event : events) {
					if ((event.getNodeEnum().equals(NodeEnum.Task) == true) && 
							(eNode.getName().equals(event.getName()) == true)) {
						return true;
					}
				}
				break;
			default:
				return false;
			}
			
			return false;
		}
		
		public InferenceCell generateInferenceCell(Neo4jData data, boolean isEvent) {
			InferenceCellApplication cell = new InferenceCellApplication();
			
			if (isEvent == true) {
				cell.setNodeEnum(NodeEnum.valueOf(data.getProperties().get("nodeEnum").get("val")));
				if (data.getProperties().containsKey("name") == true) {
					cell.setName(data.getProperties().get("name").get("val"));
				}
			} else {
				cell.setNodeEnum(NodeEnum.valueOf(data.getLabels().get(0)));
				cell.setName(data.getProperties().get("name").get("val"));
				cell.setInstance(cell.getNodeEnum().name() + "-" + cell.getName());
			}
			
			return cell;
		}
		
	}
	
	public static class InferenceCellApplication extends InferenceCell {
		
		private String app = null;
		
		public void setApp(String app) {
			this.app = app;
		}
		
		public String getApp() {
			return this.app;
		}
		
		public InferenceCell generateInferenceCell(AlarmInfo alarm) {
			InferenceCellApplication cell = new InferenceCellApplication();
			
			cell.setNodeEnum(alarm.getNodeEnum());
			cell.setName(alarm.getName());
			cell.setApp(alarm.getApp());
			
			cell.setInstance(alarm.getNodeEnum().name() + "-" + alarm.getName() + "-" + alarm.getApp());
			
			return cell;
		}
		
		public InferenceCell generateInferenceCell(Neo4jData data, boolean isEvent) {
			InferenceCellApplication cell = new InferenceCellApplication();
			
			if (isEvent == true) {
				cell.setNodeEnum(NodeEnum.valueOf(data.getProperties().get("nodeEnum").get("val")));
				if (data.getProperties().containsKey("name") == true) {
					cell.setName(data.getProperties().get("name").get("val"));
				}
				if (data.getProperties().containsKey("app") == true) {
					cell.setName(data.getProperties().get("app").get("val"));
				}
			} else {
				cell.setNodeEnum(NodeEnum.valueOf(data.getLabels().get(0)));
				cell.setName(data.getProperties().get("name").get("val"));
				cell.setApp(data.getProperties().get("app").get("val"));
				
				cell.setInstance(cell.getNodeEnum().name() + "-" + cell.getName() + "-" + cell.getApp());
			}
			
			return cell;
		}
		
		public List<InferenceCell> getRelationInferenceCell(Session session, InferenceCell cell) {
			InferenceCellApplication cellApp = (InferenceCellApplication) cell;
			
			List<InferenceCell> cells = new ArrayList<InferenceCell>();
			
			Result result = session.run( "MATCH (n:" + cell.getNodeEnum().name() + " {name: $name, app: $app}) - [*1] -> (node) RETURN node",
                    Values.parameters("name", cell.getName(), "app", cellApp.getApp()));
			while (result.hasNext()) {
				Record record = result.next();
				
				String gstr = gson.toJson(record.get("node").asObject());
				Neo4jData data = gson.fromJson(gstr, Neo4jData.class);
				cells.add(cellFactory.get(data.getLabels().get(0)).generateInferenceCell(data, false));
			}
			
			result = session.run( "MATCH (n:" + cell.getNodeEnum().name() + " {name: $name, app: $app}) <- [*1] - (node) RETURN node",
                    Values.parameters("name", cell.getName(), "app", cellApp.getApp()));
			while (result.hasNext()) {
				Record record = result.next();
				
				String gstr = gson.toJson(record.get("node").asObject());
				Neo4jData data = gson.fromJson(gstr, Neo4jData.class);
				cells.add(cellFactory.get(data.getLabels().get(0)).generateInferenceCell(data, false));
			}
			
			return cells;
		}
		
		public List<InferenceCell> getEffectInferenceCell(Session session, InferenceCell cell) {
			List<InferenceCell> cells = new ArrayList<InferenceCell>();
			
			Result result = session.run( "MATCH (n: Event {nodeEnum: $nodeEnum, group:$group}) - [*1] -> (node) RETURN node",
                    Values.parameters("nodeEnum", cell.getNodeEnum().name(), "name", cell.getName(), "group", "group1"));
			while (result.hasNext()) {
				Record record = result.next();
				
				String gstr = gson.toJson(record.get("node").asObject());
				Neo4jData data = gson.fromJson(gstr, Neo4jData.class);
				cells.add(cellFactory.get(data.getProperties().get("nodeEnum").get("val")).generateInferenceCell(data, true));
			}

			result = session.run( "MATCH (n: Event {nodeEnum: $nodeEnum, app: $app}) - [*1] -> (node) RETURN node",
                    Values.parameters("nodeEnum", cell.getNodeEnum().name(), "app", ((InferenceCellApplication)cell).getApp()));
			while (result.hasNext()) {
				Record record = result.next();
				
				String gstr = gson.toJson(record.get("node").asObject());
				Neo4jData data = gson.fromJson(gstr, Neo4jData.class);
				cells.add(cellFactory.get(data.getProperties().get("nodeEnum").get("val")).generateInferenceCell(data, true));
			}
			
			return cells;
		}
		
		public boolean isRootCause(InferenceCell eNode, List<InferenceCell> events) {
			switch (eNode.getNodeEnum()) {
			case Application:
				InferenceCellApplication appCell = (InferenceCellApplication)eNode;
				for (InferenceCell event : events) {
					if ((event.getNodeEnum().equals(NodeEnum.Application) == true)) {
						InferenceCellApplication appEvent = (InferenceCellApplication) event;
						if (appCell.getApp().equals(appEvent.getApp()) == true) {
							return true;
						}
					}
				}
				break;
			case Task:
				for (InferenceCell event : events) {
					if (event.getNodeEnum().equals(NodeEnum.Task) == true) {
						return true;
					}
				}
				break;
			default:
				return false;
			}
			
			return false;
		}
		
	}

}
