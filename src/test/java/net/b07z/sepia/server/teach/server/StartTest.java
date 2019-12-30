package net.b07z.sepia.server.teach.server;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.fail;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import net.b07z.sepia.server.core.data.Answer;
import net.b07z.sepia.server.core.data.Command;
import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.server.ConfigDefaults;
import net.b07z.sepia.server.core.server.FakeRequest;
import net.b07z.sepia.server.core.server.FakeResponse;
import net.b07z.sepia.server.core.tools.Converters;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.users.LocalTestAccount;
import net.b07z.sepia.server.teach.database.Elasticsearch;
import net.b07z.sepia.server.teach.database.ElasticsearchTest;
import net.b07z.sepia.server.teach.server.Config;
import net.b07z.sepia.server.teach.server.Start;

@Ignore("Test need to be updated and are temporarily deactivated!") 		//TODO: update tests
public class StartTest {
	
	private static Elasticsearch es;
	
	@BeforeClass
	public static void setup() throws IOException {
		es = new Elasticsearch(ElasticsearchTest.UNIT_TEST_ES, 
				ElasticsearchTest.UNIT_TEST_ES_AUTH_TYPE, ElasticsearchTest.UNIT_TEST_ES_AUTH_DATA);
		
		//TODO: clean up DB
	}

	@Test
	@Ignore("Only for interactive development")
	public void testInteractive() throws ParseException, InterruptedException {
		Thread.sleep(1000);  // avoid "all shards failed" issue
		String personalCommandsJson1 = Start.getPersonalCommands(new FakeRequest("language=en",
				"include_public=true",
				"searchText=What will you have for lunch today?"), new FakeResponse());
		JSONParser parser = new JSONParser();
		JSONObject json = (JSONObject) parser.parse(personalCommandsJson1);
		System.out.println(json);
	}
	
	@Test
	public void testCmdSummaryBuilder() {
		String cmd = "weather";
		JSONObject params = new JSONObject();
		JSON.add(params, "<place>", "Berlin");
		JSON.add(params, "<time>", "today");
		String cmd_summary = Converters.makeCommandSummary(cmd, params);
		//System.out.println(cmd_summary); 		//debug
		assertThat(cmd_summary.matches("weather;;place=Berlin;;time=today;;|weather;;time=today;;place=Berlin;;"), is(true));
	}
	
	@Test
	public void testSubmitAndVoteSentence() throws IOException, ParseException {
		//submit a private test sentence
		FakeRequest request1 = new FakeRequest(
				"sentence=i am looking for a döner", "tagged_sentence=i am looking for a <location>",
				"language=en", "command=search", "public=no", "local=no",
				"params={\"<location>\": \"döner\"}", "user_location=52.3, 7.9"
		);
		
		String jsonStr = Start.submitPersonalCommand(request1, new FakeResponse());
		
		JSONObject json = getJson(jsonStr);
		assertThat(json.get("result").toString(), is("success"));
		es.refresh();
		
		String sentencesToBeChecked = Start.getPersonalCommands(new FakeRequest(), new FakeResponse());
		JSONObject sentences = getJson(sentencesToBeChecked);
		JSONArray result = (JSONArray) sentences.get("result");
		assertThat(result.size(), is(1));
		
		JSONObject node = (JSONObject) result.get(0);
		String id = node.get("id").toString();
		assertFalse(es.search(Config.DB_COMMANDS, Command.COMMANDS_TYPE, "*").toJSONString().contains("Good"));

		//vote for a test sentence with the same user
		FakeRequest request2 = new FakeRequest("id=" + id, "vote=Good", "text=i am looking for a döner", "language=en");
		String jsonResult = Start.voteSentence(request2, new FakeResponse());
		assertSuccess(jsonResult);
		es.refresh();
		assertTrue(es.search(Config.DB_COMMANDS, Command.COMMANDS_TYPE, "*").toJSONString().contains("Good"));

		//submit a public test sentence with the same user
		FakeRequest request3a = new FakeRequest(
				"sentence=i am looking for a snack", "language=en", "command=search", "public=yes", "local=no",
				"params={\"<location>\": \"snack\"}"
		);
		Start.submitPersonalCommand(request3a, new FakeResponse());
		es.refresh();
		
		//submit a private test sentence with a different user
		FakeRequest request3b = new FakeRequest(
				"sentence=i am looking for a snack", "language=en", "command=search", "public=no", "local=no",
				"params={\"<location>\": \"snack\"}"
		);
		request3b.setAccount(new LocalTestAccount("fakeUser2@example.com"));
		Start.submitPersonalCommand(request3b, new FakeResponse());
		es.refresh();
		
		//submit a private test sentence with a different user and different language
		FakeRequest request3c = new FakeRequest(
				"sentence=ich suche etwas kleines zu Essen", "language=de", "command=search", "public=no", "local=no",
				"params={\"<location>\": \"snack\"}"
		);
		request3c.setAccount(new LocalTestAccount("fakeUser2@example.com"));
		Start.submitPersonalCommand(request3c, new FakeResponse());
		es.refresh();

		// Test getting personal commands:
		//--match 1
		JSONParser parser = new JSONParser();
		String personalCommandsJson1 = Start.getPersonalCommands(new FakeRequest("language=en", "include_public=false", "searchText=i am looking for a pizza"), new FakeResponse());
		JSONObject personalCommands1 = (JSONObject) parser.parse(personalCommandsJson1);
		JSONArray commands1 = (JSONArray) personalCommands1.get("result");
		//System.out.println("RESULT getPersonalCommands(): " + commands.toString());				//debug
		assertThat(commands1.size(), is(1));  // one hit, the other doc is public
		//--match 2
		String personalCommandsJson2 = Start.getPersonalCommands(new FakeRequest("language=en", "include_public=false", "searchText=snack"), new FakeResponse());
		JSONObject personalCommands2 = (JSONObject) parser.parse(personalCommandsJson2);
		JSONArray commands2 = (JSONArray) personalCommands2.get("result");
		assertThat(commands2.size(), is(0));  // zero hits, the searchText should not match
	}

	@Test
	public void testVoteAnswer() throws IOException, ParseException {
		setupAnswerData(es, "/server/answer.json");
		String id = getFirstDocId();
		String jsonResult = Start.voteAnswer(new FakeRequest("id=" + id, "language=de", "text=war das jetzt ein ja oder ein nein <user_name>?", "vote=Good"), new FakeResponse());
		es.refresh();
		assertTrue(jsonResult.contains("success"));
		JSONObject newResult = es.search(Config.DB_ANSWERS, Answer.ANSWERS_TYPE, "*");
		assertTrue(newResult.toJSONString().contains("vote\":\"Good"));
	}
	
	@Test
	public void testSubmitAndRetrieveFeedbackReports() throws IOException, ParseException {
		//submit report feedback
		
		//-build data 1
		JSONObject data = new JSONObject();
		JSON.add(data, "text", "dies ist ein Test");
		JSON.add(data, "answer", "Zwei Pfund Kartoffelsalat!");
		JSON.add(data, "cmd", "chat;;reply=Zwei Pfund Kartoffelsalat!");
		JSON.add(data, "location", "51.5, 7.5");
		JSON.add(data, "language", "de");
		JSON.add(data, "timestamp", System.currentTimeMillis());
		JSON.add(data, "client", "web_app_v5.900");
		JSON.add(data, "info", "report");
		//-build id 1
		String itemId = JSON.getString(data, "text");
		itemId = Converters.makeIDfromSentence(itemId);
		//-add 1
		FakeRequest request = new FakeRequest(
				"action=submit", "language=de", "info=report", "data=" + data.toString()
		);
		String jsonStr = Start.feedback(request, new FakeResponse());
		JSONObject json = getJson(jsonStr);
		assertThat(json.get("result").toString(), is("processing"));
		
		//give threads time to write
		try {	Thread.sleep(1000); 	} catch (InterruptedException e) {	e.printStackTrace(); }
		es.refresh();
		
		//-build data 2
		data = new JSONObject();
		JSON.add(data, "text", "this is the second test");
		JSON.add(data, "answer", "Two pounds of Leberwurst!");
		JSON.add(data, "cmd", "chat;;reply=Two pounds of Leberwurst");
		JSON.add(data, "location", "52.5, 8.5");
		JSON.add(data, "language", "en");
		JSON.add(data, "timestamp", System.currentTimeMillis());
		JSON.add(data, "client", "web_app_v5.900");
		JSON.add(data, "info", "report");
		//-build id 2
		itemId = JSON.getString(data, "text");
		itemId = Converters.makeIDfromSentence(itemId);
		//-add 2
		request = new FakeRequest(
				"action=submit", "language=en", "info=report", "data=" + data
		);
		request.setAccount(new LocalTestAccount("fakeUser2@example.com"));
		jsonStr = Start.feedback(request, new FakeResponse());
		json = getJson(jsonStr);
		assertThat(json.get("result").toString(), is("processing"));
		
		//give threads time to write
		try {	Thread.sleep(1000); 	} catch (InterruptedException e) {	e.printStackTrace(); }
		es.refresh();
		
		//get report feedback without filters
		request = new FakeRequest(
				"action=retrieve", "info=report", "from=0", "size=10"
		);
		String reportedFeedback = Start.feedback(request, new FakeResponse());
		JSONObject feedback = getJson(reportedFeedback);
		//System.out.println(feedback.toString()); 		//debug
		JSONArray result = (JSONArray) feedback.get("result");
		assertThat(result.size(), is(2));
		
		//get report feedback with filters - user
		request = new FakeRequest(
				"action=retrieve", "info=report", "user=fakeUser2@example.com", "from=0", "size=10"//, "language=en"
		);
		reportedFeedback = Start.feedback(request, new FakeResponse());
		feedback = getJson(reportedFeedback);
		//System.out.println(feedback.toString()); 		//debug
		result = (JSONArray) feedback.get("result");
		assertThat(result.size(), is(1));
	}

	@Test
	public void testModifyAnswer() throws IOException, ParseException {
		setupAnswerData(es, "/server/answer.json");
		String id = getFirstDocId();
		String jsonResult = Start.modifyAnswer(new FakeRequest("id=" + id, "language=de", "oldText=war das jetzt ein ja oder ein nein <user_name>?", "newText=neue Antwort"), new FakeResponse());
		es.refresh();
		assertTrue(jsonResult.contains("success"));
		JSONObject newResult = es.search(Config.DB_ANSWERS, Answer.ANSWERS_TYPE, "*");
		assertTrue(newResult.toJSONString().contains("old_text\":\"war das jetzt ein ja oder ein nein <user_name>?"));
		assertTrue(newResult.toJSONString().contains("text\":\"neue Antwort"));
		assertTrue(newResult.toJSONString().contains("machine_translated\":false"));
	}

	private String getFirstDocId() {
		JSONObject json = es.search(Config.DB_ANSWERS, Answer.ANSWERS_TYPE, "*");
		JSONObject hit = (JSONObject) ((JSONArray) json.get("hits")).get(0);
		return hit.get("_id").toString();
	}

	@Test
	public void testAddSentence() throws IOException, ParseException {
		setupCommandsData(es, "/server/translation.json");
		JSONObject json = es.search(Config.DB_COMMANDS, Command.COMMANDS_TYPE, "*");
		JSONObject hit = (JSONObject) ((JSONArray) json.get("hits")).get(0);
		String id = hit.get("_id").toString();
		String jsonResult = Start.addSentence(new FakeRequest("id=" + id, "language=de", "text=this is the new text"), new FakeResponse());
		es.refresh();
		assertTrue(jsonResult.contains("success"));
		JSONObject newResult = es.search(Config.DB_COMMANDS, Command.COMMANDS_TYPE, "*");
		assertTrue(newResult.toJSONString().contains("text\":\"this is the new text"));
		assertTrue(newResult.toJSONString().contains("text\":\"Hello, <user>, how are you?"));
		assertTrue(newResult.toJSONString().contains("text\":\"Hallo, <user>, wie geht es Dir?"));
	}

	@Test
	public void testGetAnswersByType() throws IOException, ParseException {
		setupAnswerData(es, "/server/answer.json");
		LocalTestAccount user = new LocalTestAccount();
		user.setUserRole(Role.developer.name());
			
		String result = Start.getAnswersByType(new FakeRequest(user, "answerType=yes_no_ask_0a", "language=de", "user="+ConfigDefaults.defaultAssistantUserId), new FakeResponse());
		assertTrue(result.contains("jetzt"));
		String result2 = Start.getAnswersByType(new FakeRequest(user, "answerType=yes_no_ask_0a"), new FakeResponse());
		assertFalse(result2.contains("jetzt"));
		String result2b = Start.getAnswersByType(new FakeRequest(user, "answerType=yes_no_ask", "user="+ConfigDefaults.defaultAssistantUserId), new FakeResponse());
		assertFalse(result2b.contains("jetzt"));
		String result3 = Start.getAnswersByType(new FakeRequest(user, "answerType=yes_no_ask_0a", "language=de", "user="+ConfigDefaults.defaultAssistantUserId), new FakeResponse());
		assertTrue(result3.contains("jetzt"));
		String result4 = Start.getAnswersByType(new FakeRequest(user, "answerType=yes_no_ask_0a", "language=de", "user=jim sample"), new FakeResponse());
		assertFalse(result4.contains("jetzt"));
		String result5 = Start.getAnswersByType(new FakeRequest(user, "answerType=yes_no_ask_0a", "language=xx", "user="+ConfigDefaults.defaultAssistantUserId), new FakeResponse());
		assertFalse(result5.contains("jetzt"));
	}

	@Test
	public void testAddAnswer() throws IOException, ParseException {
		LocalTestAccount user = new LocalTestAccount();
		user.setUserID("fakeMe@example.com"); 			//define a user
		user.setUserRole(Role.seniordev.name(), Role.developer.name(), Role.tester.name());
		
		String result = Start.addAnswer(new FakeRequest(user, "language=en", "type=xyz_answer", "text=My answer!",
				"polite=true", "repetition=0", "mood=5"), new FakeResponse());
		assertSuccess(result);
		String result2 = Start.addAnswer(new FakeRequest(user, "language=en", "type=xyz_answer_2", "text=My other answer!",
				"polite=true", "repetition=0", "mood=5", "systemdefault=true"), new FakeResponse());
		assertSuccess(result2);
		user.setUserID("fakeJim@example.com");			//switch user
		String result3 = Start.addAnswer(new FakeRequest(user, "language=en", "type=xyz_answer", "text=Jim's answer!",
				"polite=true", "repetition=0", "mood=5"), new FakeResponse());
		assertSuccess(result3);
		es.refresh();
		user.setUserID("fakeMe@example.com");			//choose initial user again
		//query for "my" answers
		String resultOfQuery = Start.getAnswersByType(new FakeRequest(user, "answerType=xyz_answer"), new FakeResponse());
		assertTrue(JSON.getJArray(resultOfQuery, "entries").size() == 1);
		assertTrue(resultOfQuery.contains(user.getUserID()));
		assertTrue(resultOfQuery.contains("xyz_answer"));
		assertTrue(resultOfQuery.contains("polite"));
		assertFalse(resultOfQuery.contains("neutral"));
		//query for system answer
		String resultOfQuery2 = Start.getAnswersByType(new FakeRequest(user, "answerType=xyz_answer_2", "user="+ConfigDefaults.defaultAssistantUserId), new FakeResponse());
		assertTrue(resultOfQuery2.contains("\"" + ConfigDefaults.defaultAssistantUserId + "\""));
		assertTrue(resultOfQuery2.contains("xyz_answer_2"));
		//query for my and Jim's answers
		String resultOfQuery3 = Start.getAnswersByType(new FakeRequest(user, "answerType=xyz_answer", "user=fakeMe@example.com,fakeJim@example.com"), new FakeResponse());
		assertTrue(JSON.getJArray(resultOfQuery3, "entries").size() == 2);
		assertTrue(resultOfQuery3.contains("xyz_answer"));
		//query for answers of users that don't exist
		String resultOfQuery4 = Start.getAnswersByType(new FakeRequest(user, "answerType=xyz_answer", "user=me, jim, john, jack"), new FakeResponse());
		assertTrue(JSON.getJArray(resultOfQuery4, "entries").size() == 0);
		
		//TODO: add a test where we try to access answers of other users with missing developer role (remove authRequired from roleCheck first)
	}

	private void setupCommandsData(Elasticsearch es, String jsonFile) throws IOException {
		URL url = StartTest.class.getResource(jsonFile);
		String json = Resources.toString(url, Charsets.UTF_8);
		es.writeDocument(Config.DB_COMMANDS, Command.COMMANDS_TYPE, json);
		es.refresh();
	}

	private void setupAnswerData(Elasticsearch es, String jsonFile) throws IOException {
		URL url = StartTest.class.getResource(jsonFile);
		String json = Resources.toString(url, Charsets.UTF_8);
		es.writeDocument(Config.DB_ANSWERS, Answer.ANSWERS_TYPE, json);
		es.refresh();
	}

	private void assertSuccess(String jsonResult) {
		JSONObject obj = getJson(jsonResult);
		Object result = obj.get("result");
		if (result != null && !result.toString().equals("success")) {
			fail("Didn't get 'success' reply: " + jsonResult);
		}
	}

	// TODO: add more tests

	private JSONObject getJson(String jsonStr) {
		String noCallback = jsonStr.trim().replaceFirst("^null\\(", "").replaceFirst("\\);$", "");
		JSONObject jsonObject = JSON.parseStringOrFail(noCallback);
		return jsonObject;
	}

}