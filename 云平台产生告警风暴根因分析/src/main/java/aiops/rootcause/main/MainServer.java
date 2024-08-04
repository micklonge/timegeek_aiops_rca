package aiops.rootcause.main;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

import aiops.rootcause.main.InferenceEngine.InferenceCell;

public class MainServer {
	
	private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
	
	private static void learnKnowledge(Session session) throws Exception {
		Result result = session.run( "MATCH (n:Tag) RETURN n");
		if (result.hasNext() == true) {
			return;
		}
		
		session.run("CREATE (es:Event {nodeEnum:$server, group:$group}),"
				+ "(ea:Event {nodeEnum:$application, group:$group}),"
				+ "(et:Event {nodeEnum:$task, group:$group}),"
				+ "(es)-[:EFFECT]->(ea),"
				+ "(ea)-[:EFFECT]->(et)", 
				Values.parameters("server", "Server", "application", "Application", "task", "Task", "group", "group1"));
		
		session.run("CREATE (etd:Event {nodeEnum:$task, name:$decoder}),"
				+ "(etc:Event {nodeEnum:$task, name:$computer}),"
				+ "(etd)-[:EFFECT]->(etc)", 
				Values.parameters("task", "Task", "decoder", "decoder", "computer", "computer"));
		
		session.run("CREATE (ea1:Event {nodeEnum:$application, app:$app1}),"
				+ "(ea2:Event {nodeEnum:$application, app:$app2}),"
				+ "(ea3:Event {nodeEnum:$application, app:$app3}),"
				+ "(ea1)-[:EFFECT]->(ea2),"
				+ "(ea2)-[:EFFECT]->(ea3)", 
				Values.parameters("application", "Application", "app1", "app1", "app2", "app2", "app3", "app3"));
		
		session.run("CREATE (tag: Tag)");
	}
	
	private static void createAlarms(List<AlarmInfo> alarms) throws Exception {
		alarms.add(new AlarmInfo(NodeEnum.Server, "172.168.1.2"));
		alarms.add(new AlarmInfo(NodeEnum.Application, "172.168.1.2", "app1"));
		alarms.add(new AlarmInfo(NodeEnum.Task, "decoder"));
		alarms.add(new AlarmInfo(NodeEnum.Task, "computer"));
		
		alarms.add(new AlarmInfo(NodeEnum.Server, "172.168.0.2"));
		alarms.add(new AlarmInfo(NodeEnum.Application, "172.168.0.2", "app1"));
	}
	
	public static void main(String[] args) throws Exception {
		List<AlarmInfo> alarms = new ArrayList<AlarmInfo>();
		
		Driver driver = GraphDatabase.driver("bolt://127.0.0.1:7687", AuthTokens.basic("neo4j", "1"));
		
		Session session = driver.session();
		
		// 知识输入 input
		learnKnowledge(session);
		
		// create alarms
		createAlarms(alarms);
		
		// example1 output
		List<InferenceCell> cells = InferenceEngine.inference(session, alarms);
		for (InferenceCell cell : cells) {
		      System.out.println(gson.toJson(cell));
		      System.out.println();
		}
		
		session.close();
		driver.close();
	}

}
