package net.b07z.sepia.server.teach.database;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import net.b07z.sepia.server.core.data.Answer;
import net.b07z.sepia.server.core.data.CmdMap;
import net.b07z.sepia.server.core.data.Command;
import net.b07z.sepia.server.core.data.Language;
import net.b07z.sepia.server.core.tools.Connectors;
import net.b07z.sepia.server.core.tools.Converters;
import net.b07z.sepia.server.core.tools.DateTime;
import net.b07z.sepia.server.core.tools.EsQueryBuilder;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.tools.EsQueryBuilder.QueryElement;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.server.teach.data.Vote;
import net.b07z.sepia.server.teach.server.Config;

/**
 * Abstraction layer on top of Elasticsearch.
 * 
 * @author Florian Quirin
 */
public class Elasticsearch implements TeachDatabase {

	private static final Logger log = LoggerFactory.getLogger(Elasticsearch.class);
	private static final String ES_COMMANDS_PATH = Config.DB_COMMANDS + "/" + Command.COMMANDS_TYPE;
	private static final String ES_ANSWERS_PATH = Config.DB_ANSWERS + "/" + Answer.ANSWERS_TYPE;

	private final JsonFactory factory = new JsonFactory();
	private final String esServer;

	public Elasticsearch() {
		esServer = ConfigElasticSearch.getEndpoint(Config.defaultRegion);
	}

	public Elasticsearch(String serverUrl) {
		esServer = serverUrl;
	}

	//-------INTERFACE IMPLEMENTATIONS---------
	
	@Override
	public void refresh() {
		String url = esServer + "/_refresh";
		JSONObject result = Connectors.httpGET(url);
		failOnRestError(url, result);
	}
	
	@Override
	public int setItemData(String index, String type, String itemId, JSONObject data) {
		writeDocument(index, type, itemId, data);
		return 0; 		//always 0 or error in this implementation of the interface
	}
	
	@Override
	public JSONObject setAnyItemData(String index, String type, JSONObject data) {
		String id = writeDocument(index, type, data);
		return JSON.make("code", 0, "_id", id);		//always 0 or error in this implementation of the interface
	}
	
	@Override
	public JSONObject getItem(String index, String type, String itemId) {
		return getDocument(index, type, itemId);
	}
	
	@Override
	public JSONObject getItemFiltered(String index, String type, String itemId, String[] filters) {
		//convert filters to sources-string
		StringBuilder sb = new StringBuilder();
		for (String f : filters){
			sb.append(f.trim()).append(",");
		}
		String sources = sb.toString().replaceFirst(",$", "").trim();
		return getDocument(index, type, itemId, sources);
	}
	
	@Override
	public int updateItemData(String index, String type, String item_id, JSONObject data) {
		throw new RuntimeException("Method not implemented yet");
		//return 0;
	}

	@Override
	public JSONObject searchSimple(String path, String searchTerm) {
		JSONObject json = searchSimple(path, searchTerm, 0, 100);
		return (JSONObject) json.get("hits");
	}
	
	@Override
	public JSONObject search(String index, String type, String query) {
		JSONObject json = searchSimple(index + "/" + type, query, 0, 100);
		return (JSONObject) json.get("hits");
	}
	
	@Override
	public JSONObject searchSimple(String path, String searchTerm, int from, int size){
		String pathWithSlash = path.endsWith("/") ? path : path + "/";
		try {
			String url = esServer + "/" + pathWithSlash + "_search?q=" + URLEncoder.encode(searchTerm, "UTF-8") + "&from=" + from + "&size=" + size;
			JSONObject result = Connectors.httpGET(url);
			failOnRestError(url, result);
			return result;
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public JSONObject searchByJson(String path, String queryJson) {
		String pathWithSlash = path.endsWith("/") ? path : path + "/";
		String url = esServer + "/" + pathWithSlash + "_search";
		JSONObject result = Connectors.httpPOST(url, queryJson, null);
		failOnRestError(url, result);
		return result;
	}
	
	@Override
	public int deleteItem(String index, String type, String itemId) {
		deleteDocument(index, type, itemId);
		return 0; 		//always 0 or error in this implementation of the interface
	}
	
	@Override
	public int deleteAnything(String path) {
		String url = esServer + "/" + path;
		JSONObject result = Connectors.httpDELETE(url);
		failOnRestError(url, result);
		return 0; 		//always 0 or error in this implementation of the interface
	}

	@Override
	public JSONObject deleteByJson(String path, String jsonQuery) {
		String pathWithSlash = path.endsWith("/") ? path : path + "/";
		String url = esServer + "/" + pathWithSlash + "_delete_by_query";
		JSONObject result = Connectors.httpPOST(url, jsonQuery, null);
		failOnRestError(url, result);
		return result;
	}
	
	//--------ELASTICSEARCH METHODS---------

	// Delete path but don't fail with error, e.g. if path doesn't exist
	public void deleteSilently(String path) {
		Connectors.httpDELETE(esServer + "/" + path);
	}
	
	/**
	 * Delete document at "index/type/id".
	 * @param index - index name, e.g. "account"
	 * @param type - type name, e.g. "user"
	 * @param id - id name/number, e.g. user_id
	 */
	public void deleteDocument(String index, String type, String id){
		Objects.requireNonNull(id);
		if (id.trim().isEmpty()) {
			throw new RuntimeException("Cannot delete doc, empty id provided (index/type: " + index + "/" + type + ")");
		}
		String url = esServer + "/" + index + "/" + type + "/" + id;
		JSONObject result = Connectors.httpDELETE(url);
		failOnRestError(url, result);
	}

	/**
	 * Write document for "id" of "type" in "index".
	 * @param index - index name, e.g. "account"
	 * @param type - type name, e.g. "user"
	 * @param id - id name/number, e.g. user_id
	 * @param data - JSON data to put inside id
	 */
	public void writeDocument(String index, String type, String id, JSONObject data){		
		writeDocument(index, type + "/" + id, data);
	}

	/**
	 * Write document for random id of "type" in "index".
	 * @param index - index name, e.g. "account"
	 * @param type - type name, e.g. "user"
	 * @param data - JSON data to put inside id
	 * @return the ES id of the new document 
	 */
	public String writeDocument(String index, String type, JSONObject data){		
		String json = data.toJSONString();
		return writeDocument(index, type, json);
	}
	
	/**
	 * Write document for random id of "type" in "index".
	 * @param index - index name, e.g. "account"
	 * @param type - type name, e.g. "user"
	 * @param json - JSON data as string to put inside id
	 * @return the ES id of the new document 
	 */
	public String writeDocument(String index, String type, String json){		
		HashMap<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Content-Length", Integer.toString(json.getBytes().length));
		String url = esServer + "/" + index + "/" + type;
		JSONObject result = Connectors.httpPOST(url, json, headers);
		failOnRestError(url, result);
		return (String)result.get("_id");
	}
	
	/**
	 * Get document at path "index/type/id".
	 * @param index - index name, e.g. "account"
	 * @param type - type name, e.g. "user"
	 * @param id - id name/number, e.g. user_id
	 * @return JSONObject with document data or error
	 */
	public JSONObject getDocument(String index, String type, String id){		
		String url = esServer + "/" + index + "/" + type + "/" + id;
		JSONObject result = Connectors.httpGET(url);
		failOnRestError(url, result);
		return result;
	}
	
	/**
	 * Post any JSON query to ES.
	 */
	/*
	public JSONObject submit(String path, String json) {
		String pathWithSlash = path.endsWith("/") ? path : path + "/";
		String url = esServer + "/" + pathWithSlash;
		JSONObject result = Connectors.httpPOST(url, json);
		failOnRestError(url, result);
		return result;
	}
	*/
	
	/**
	 * Get document at path "index/type/id" with filtered entries.
	 * @param index - index name, e.g. "account"
	 * @param type - type name, e.g. "user"
	 * @param id - id name/number, e.g. user_id
	 * @param sources - entries in the document you want to retrieve, e.g. "name,address,email", separated by a simple ",". All empty space is removed.
	 * @return JSONObject with document data or null. If sources are missing they are ignored.
	 */
	public JSONObject getDocument(String index, String type, String id, String sources){
		return getDocument(index, type, id + "?_source=" + sources.replaceAll("\\s+", "").trim());
	}

	
	//------------------------ SPECIFIC METHODS FOR THE API -----------------------------
	
	@Override
	public List<CmdMap> getCustomCommandMappings(String userId, String customOrSystem, HashMap<String, Object> filters){
		JSONObject data = new JSONObject();
		data = getItem(Config.DB_USERDATA, CmdMap.MAP_TYPE, userId + "/_source");
		List<CmdMap> map = CmdMap.makeMapList((JSONArray) data.get(customOrSystem));
		return map;
	}
	@Override
	public void setCustomCommandMappings(String userId, String customOrSystem, Set<CmdMap> mappings, HashMap<String, Object> filters){
		//load existing map and add to new set
		boolean overwrite = (boolean) filters.get("overwrite");
		if (!overwrite){
			List<CmdMap> map = getCustomCommandMappings(userId, customOrSystem, null);
			mappings.addAll(map);
		}
		//add new map
		JSONObject customOrSystemData = new JSONObject();
		JSONArray mappingsArray = new JSONArray();
		for (CmdMap cm : mappings){
			JSON.add(mappingsArray, cm.getJSON());
		}
		JSON.put(customOrSystemData, customOrSystem, mappingsArray);
		
		setItemData(Config.DB_USERDATA, CmdMap.MAP_TYPE, userId, customOrSystemData);
	}

	@Override
	public JSONArray getPersonalCommands(HashMap<String, Object> filters) {
		
		//in principle these parameters should already be checked in main, but ...
		String userIds = (filters.containsKey("userIds"))? (String) filters.get("userIds") : "";
		//List<String> userIdList = new ArrayList<>();
		//userIdList.addAll(Arrays.asList(userIds.split(",\\s*")));
		//Set<String> userIdSet = new HashSet<>(userIdList);
		if (userIds.isEmpty()){
			log.warn("Empty user list, returning empty result. Filters: " + filters);
			return new JSONArray();
		}
		List<String> userIdSet = Arrays.asList(userIds);
		String language = (filters.containsKey("language"))? (String) filters.get("language") : "en";
		boolean includePublic = (filters.containsKey("includePublic"))? (boolean) filters.get("includePublic") : true;
		String searchText = (filters.containsKey("searchText"))? (String) filters.get("searchText") : "";
		boolean matchExactText = (filters.containsKey("matchExactText"))? (boolean) filters.get("matchExactText") : false;
		
		StringWriter sw = new StringWriter();
		try {
			try (JsonGenerator g = factory.createGenerator(sw)) {
				startNestedQuery(g, 0);

				//TODO: add info about the "size" of results somewhere here?

				// match at least one of the users:
				g.writeArrayFieldStart("should");
					for (String userId : userIdSet) {
						g.writeStartObject();
							g.writeObjectFieldStart("match");
								g.writeObjectFieldStart("sentences.user");
									g.writeStringField("query", userId);
									g.writeStringField("analyzer", "keylower");
								g.writeEndObject();
							g.writeEndObject();
						g.writeEndObject();
					}
				g.writeEndArray();
				g.writeNumberField("minimum_should_match", 1);
				
				g.writeArrayFieldStart("must");

				g.writeStartObject();
					g.writeObjectFieldStart("match");
						g.writeStringField("sentences.language", language);
					g.writeEndObject();
				g.writeEndObject();

				if (!includePublic){
					g.writeStartObject();
						g.writeObjectFieldStart("match");
							g.writeBooleanField("sentences.public", false);
						g.writeEndObject();
					g.writeEndObject();
				}
				
				if (!searchText.isEmpty()){
					g.writeStartObject();
						g.writeObjectFieldStart("multi_match");
							//g.writeObjectFieldStart("sentences.text");
								g.writeStringField("query", searchText);
								g.writeStringField("analyzer", "standard"); 		//use: asciifolding filter?
								if (matchExactText){
									g.writeStringField("operator", "and");
								}else{
									g.writeStringField("operator", "or");
								}
								g.writeArrayFieldStart("fields");
									g.writeString("sentences.text");
									g.writeString("sentences.tagged_text");			//use this with "and" and the right pattern to replace <...> tags
								g.writeEndArray();
							//g.writeEndObject();
						g.writeEndObject();
					g.writeEndObject();
				}

				endNestedQuery(g);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		//System.out.println(sw.toString()); 		//debug
		
		JSONObject result = searchByJson(ES_COMMANDS_PATH, sw.toString());
		JSONArray output = new JSONArray();
		JSONArray hits = JSON.getJArray(result, new String[]{"hits", "hits"});
		if (hits != null){
			for (Object hitObj : hits) {
				JSONObject hit = (JSONObject) hitObj;
				JSONObject hitSentence = new JSONObject();
				JSONObject source = (JSONObject) hit.get("_source");
				JSON.add(hitSentence, "sentence", source.get("sentences"));
				String id = (String) hit.get("_id");
				JSON.add(hitSentence, "id", id);
				JSON.add(output, hitSentence);
			}
		}
		return output;
	}
	@Override
	public JSONArray getAllPersonalCommands(HashMap<String, Object> filters) {
		long from = Converters.obj2LongOrDefault(filters.get("from"), -1l);		if (from == -1) from = 0;	
		String userId = (String) filters.get("userId");
		String language = (filters.containsKey("language"))? (String) filters.get("language") : "";
		boolean with_button_only = filters.containsKey("button"); 		//Note: we don't actually check the value, if its there its true!
		
		//build a nested query
		String nestPath = "sentences";
		List<QueryElement> nestedMatches = new ArrayList<>(); 
		nestedMatches.add(new QueryElement(nestPath + ".user", userId));
		if (!language.isEmpty()){
			nestedMatches.add(new QueryElement(nestPath + ".language", language));
		}
		if (with_button_only){
			nestedMatches.add(new QueryElement(nestPath + ".data.show_button", true));
		}
		JSONObject queryJson = EsQueryBuilder.getNestedBoolMustMatch(nestPath, nestedMatches);
		JSON.put(queryJson, "from", from);
		
		//collect results
		JSONObject result = searchByJson(ES_COMMANDS_PATH, queryJson.toJSONString());
		JSONArray output = new JSONArray();
		JSONArray hits = JSON.getJArray(result, new String[]{"hits", "hits"});
		if (hits != null){
			for (Object hitObj : hits) {
				JSONObject hit = (JSONObject) hitObj;
				JSONObject hitSentence = new JSONObject();
				JSONObject source = (JSONObject) hit.get("_source");
				JSON.add(hitSentence, "sentence", source.get("sentences"));
				String id = (String) hit.get("_id");
				JSON.add(hitSentence, "id", id);
				JSON.add(output, hitSentence);
			}
		}
		return output;
	}
	@Override
	public String getIdOfCommand(String userId, String language, String textToMatch){
		HashMap<String, Object> getFilters = new HashMap<String, Object>();
		getFilters.put("userIds", userId);
		getFilters.put("language", language);
		getFilters.put("searchText", textToMatch);
		getFilters.put("matchExactText", new Boolean(true));
		
		JSONArray matchingSentences = getPersonalCommands(getFilters);
		String itemId = "";
		try{
			for (Object o : matchingSentences){
				JSONObject jo = (JSONObject) o;
				JSONObject sentence = (JSONObject) JSON.getJArray(jo, new String[]{"sentence"}).get(0);
				String text = (String) sentence.get("text");
				String textTagged = (String) sentence.get("tagged_text");
				if (textToMatch.equalsIgnoreCase(text) || textToMatch.equalsIgnoreCase(textTagged)){
					itemId = (String) jo.get("id");
					break;
				}
			}
		}catch (Exception e){
			e.printStackTrace();
		}
		return itemId;
	}
	@Override
	public void submitCommand(Command cmd) {
		writeDocument(Config.DB_COMMANDS, Command.COMMANDS_TYPE, cmd.toJson());
	}
	@Override
	public void submitCommand(Command cmd, String id) {
		writeDocument(Config.DB_COMMANDS, Command.COMMANDS_TYPE, id, cmd.toJson());
	}
	@Override
	public JSONObject deleteCommand(String id, String userId) {
		//build a mixed nested and root query
		List<QueryElement> rootMatches = new ArrayList<>(); 
		rootMatches.add(new QueryElement("_id", id));
		
		String nestPath = "sentences";
		List<QueryElement> nestedMatches = new ArrayList<>(); 
		nestedMatches.add(new QueryElement(nestPath + ".user", userId));
		
		JSONObject queryJson = EsQueryBuilder.getMixedRootAndNestedBoolMustMatch(rootMatches, nestPath, nestedMatches);

		return deleteByJson(Config.DB_COMMANDS + "/" + Command.COMMANDS_TYPE, queryJson.toJSONString());
	}
	
	@Override
	public void addSentence(String docId, Language language, String text, Account userAccount) {
		JSONObject json = getDocument(Config.DB_COMMANDS, Command.COMMANDS_TYPE, docId + "/_source");
		JSONArray sentences = (JSONArray) json.get("sentences");
		JSONObject o = new JSONObject();
		// TODO: right solution would be to parse the result (a Command) and then work on the result,
		// finally writing it as JSON again:
		JSON.put(o, "language", language.name());
		JSON.put(o, "text", text);
		JSON.put(o, "user", userAccount.getUserID());
		JSON.put(o, "source", "manually_added");
		JSON.put(o, "public", true);
		JSON.put(o, "machine_translated", false);
		JSON.add(sentences, o);
		writeDocument(Config.DB_COMMANDS, Command.COMMANDS_TYPE, docId, json);
	}
	
	@Override
	public void voteSentence(String docId, String votedSentence, Language votedLanguage, Vote vote, Account account) {
		String dbIndex = Config.DB_COMMANDS;
		String dbType = Command.COMMANDS_TYPE;
		String url = esServer + "/" + dbIndex + "/" + dbType + "/" + docId + "/_source";
		JSONObject json = Connectors.simpleJsonGet(url);
		JSONArray sentences = (JSONArray) json.get("sentences");
		boolean foundText = false;
		for (Object sentenceObj : sentences) {
			JSONObject sentence = (JSONObject) sentenceObj;
			String text = sentence.get("text").toString();
			String language = sentence.get("language").toString();
			if (text.equals(votedSentence) && language.equalsIgnoreCase(votedLanguage.name())) {
				JSONArray votesArray = new JSONArray();
				JSONObject voteObj = new JSONObject();
				JSON.add(voteObj, "vote", vote.name());
				JSON.add(voteObj, "date", DateTime.getLogDate());
				JSON.add(voteObj, "user", account.getUserID());
				JSON.add(votesArray, voteObj);
				JSON.add(sentence, "votes", votesArray);
				writeDocument(dbIndex, dbType, docId, json);
				foundText = true;
				break;
			}
		}
		if (!foundText) {
			throw new RuntimeException("Text '" + votedSentence + "' (lang: '" + votedLanguage + "') not found in document " + docId);
		}
	}
	
	@Override
	public JSONObject getAnswersByType(String index, String type, String answerType, String languageOrNull, String userOrSelf) {
		//multiple users?
		String[] users = userOrSelf.split(","); 		//assume that user IDs can never have commas as characters!
		
		StringWriter sw = new StringWriter();
		try {
			try (JsonGenerator g = factory.createGenerator(sw)) {
				g.writeStartObject();
					g.writeNumberField("size", 10000);  // let's read the maximum possible
					g.writeObjectFieldStart("query");
						g.writeObjectFieldStart("bool");
							//must
							g.writeArrayFieldStart("must");
								//type
								makeJsonQueryTerm(g, "type", answerType.toLowerCase());
								//language
								if (languageOrNull != null) {
									makeJsonQueryTerm(g, "language", languageOrNull.toLowerCase());
								}
							g.writeEndArray();
							//should
							g.writeArrayFieldStart("should");
								//user
								for (String u : users){
									makeJsonQueryTerm(g, "user", u.trim().toLowerCase());
								}
							g.writeEndArray();
							g.writeNumberField("minimum_should_match", 1);  //at least one of the shoulds must match
						g.writeEndObject();
					g.writeEndObject();
				g.writeEndObject();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		//System.out.println("JSON QUERY: " + sw.toString()); 		//debug
		JSONObject result = searchByJson(ES_ANSWERS_PATH, sw.toString());
		JSONObject hits = (JSONObject) result.get("hits");
		JSONArray innerHits = (JSONArray) hits.get("hits");
		JSONObject o = new JSONObject();
		JSON.put(o, "entries", innerHits);
		return o;
	}
	
	@Override
	public JSONObject getAnswerById(String docId){
		return getDocument(Config.DB_ANSWERS, Answer.ANSWERS_TYPE, docId);
	}
	
	@Override
	public void addAnswer(Answer answer, Account account) {
		setAnyItemData(Config.DB_ANSWERS, Answer.ANSWERS_TYPE, JSON.parseStringOrFail(answer.toJsonString()));
	}
	
	@Override
	public JSONObject deleteAnswerById(String docId, String userId) {
		List<QueryElement> matches = new ArrayList<>(); 
		matches.add(new QueryElement("user", userId));
		matches.add(new QueryElement("_id", docId));
		String query = EsQueryBuilder.getBoolMustMatch(matches).toJSONString();
		return deleteByJson(Config.DB_ANSWERS + "/" + Answer.ANSWERS_TYPE, query);
	}

	@Override
	public void voteAnswer(String docId, String votedSentence, Language votedLanguage, Vote vote, Account account) {
		String dbIndex = Config.DB_ANSWERS;
		String dbType = Answer.ANSWERS_TYPE;
		String url = esServer + "/" + dbIndex + "/" + dbType + "/" + docId + "/_source";
		JSONObject sentence = Connectors.simpleJsonGet(url);
		String text = sentence.get("text").toString();
		String language = sentence.get("language").toString();
		if (text.equals(votedSentence) && language.equalsIgnoreCase(votedLanguage.name())) {
			JSONArray votesArray = new JSONArray();
			JSONObject voteObj = new JSONObject();
			JSON.add(voteObj, "vote", vote.name());
			JSON.add(voteObj, "date", DateTime.getLogDate());
			JSON.add(voteObj, "user", account.getUserID());
			JSON.add(votesArray, voteObj);
			JSON.add(sentence, "votes", votesArray);
			writeDocument(dbIndex, dbType, docId, sentence);
		} else {
			throw new RuntimeException("Text '" + votedSentence + "' (lang: '" + votedLanguage + "') not found in answer document " + docId + ", text was '" + text + "' instead");
		}
	}

	@Override
	public void modifyAnswer(String docId, Language language, String oldText, String newText) {
		String dbIndex = Config.DB_ANSWERS;
		String dbType = Answer.ANSWERS_TYPE;
		JSONObject json = getDocument(dbIndex, dbType, docId + "/_source");
		String text = json.get("text").toString();
		String languageStr = json.get("language").toString();
		if (text.equals(oldText) && languageStr.equalsIgnoreCase(language.name())) {
			JSON.put(json, "text", newText);
			JSON.put(json, "machine_translated", false);
			JSON.put(json, "old_text", oldText);
			writeDocument(dbIndex, dbType, docId, json);
		} else {
			throw new RuntimeException("Text '" + oldText + "' (lang: '" + language + "') not found in document " + docId);
		}
	}
	
	@Override
	public JSONArray getReportedFeedback(HashMap<String, Object> filters, int from, int size) {
		return getFeedback(Feedback.TYPE_REPORT, from, size, filters);
	}

	@Override
	public JSONArray getLikedFeedback(HashMap<String, Object> filters, int from, int size) {
		return getFeedback(Feedback.TYPE_LIKE, from, size, filters);
	}
	
	private JSONArray getFeedback(String type, int from, int size, HashMap<String, Object> filters) {
		JSONObject result;
		//no filters - simply search all
		if (filters == null || filters.isEmpty()){
			result = searchSimple(Feedback.INDEX + "/" + type, "*", from, size);
		//with filters - check for matches
		}else{
			result = searchByJson(Feedback.INDEX + "/" + type, makeMatchQuery(filters, from, size));
		}
		return collectHits(result);
	}

	
	private JSONArray collectHits(JSONObject result) {
		JSONObject hitsObj = (JSONObject) result.get("hits");
		JSONArray hits = (JSONArray) hitsObj.get("hits");
		JSONArray output = new JSONArray();
		for (Object hitObj : hits) {
			JSONObject hit = (JSONObject) hitObj;
			JSONObject source = (JSONObject) hit.get("_source");
			JSON.add(output, source);
		}
		return output;
	}

	@Override
	public JSONObject getLogs(String index, String type, int from, int size) {
		String jsonQuery =
				"{" +
				"    \"from\" : " + from + "," +
				"    \"size\" : " + size + "," +
				"    \"sort\" : {\"date\": \"desc\"}," +
				"    \"query\" : {" +
				"        \"match_all\" : {}" +
				"    }" +
				"}'";
		JSONObject result = searchByJson(index + "/" + type, jsonQuery);
		JSONObject hits = (JSONObject) result.get("hits");
		JSONArray innerHits = (JSONArray) hits.get("hits");
		JSONObject o = new JSONObject();
		List<Object> entries = new ArrayList<>();
		for (Object innerHit : innerHits) {
			entries.add(((JSONObject)innerHit).get("_source"));
		}
		JSON.put(o, "entries", entries);
		JSON.put(o, "totalHits", hits.get("total"));
		return o;
	}
	
	//-------------------- SEARCH HELPERS -------------------------
	
	/*
	private void startNestedQueryWithMust(JsonGenerator g) throws IOException {
		startNestedQueryWithMust(g, 0);
	}
	private void startNestedQueryWithMust(JsonGenerator g, int from) throws IOException {
		startNestedQuery(g, from);
		g.writeArrayFieldStart("must");
	}
	*/
	
	private void makeJsonQueryTerm(JsonGenerator g, String key, String value) throws IOException {
		g.writeStartObject();
			g.writeObjectFieldStart("term");
				g.writeStringField(key, value.toLowerCase());
			g.writeEndObject();
		g.writeEndObject();
	}

	private void startNestedQuery(JsonGenerator g, int from) throws IOException {
		g.writeStartObject();
		g.writeNumberField("from", from);
		g.writeObjectFieldStart("query");
		g.writeObjectFieldStart("nested");
		g.writeStringField("path", "sentences");
		g.writeObjectFieldStart("query");
		g.writeObjectFieldStart("bool");
	}

	private void startBooleanQuery(JsonGenerator g, int from, int size) throws IOException {
		g.writeStartObject();
		g.writeNumberField("from", from);
		g.writeNumberField("size", size);
		g.writeObjectFieldStart("query");
		g.writeObjectFieldStart("bool");
		g.writeArrayFieldStart("must");
	}

	private void endNestedQuery(JsonGenerator g) throws IOException {
		g.writeEndArray();
		g.writeEndObject();
		g.writeEndObject();
		g.writeEndObject();
		g.writeEndObject();
	}
	
	private void endBooleanQuery(JsonGenerator g) throws IOException {
		g.writeEndArray();
		g.writeEndObject();
		g.writeEndObject();
		g.writeEndObject();
	}

	private void failOnRestError(String url, JSONObject result){
		if (!Connectors.httpSuccess(result)){
			String error = Connectors.httpError(result);
			if (error != null) {
				//throw new RuntimeException("No success in field '" + jsonField + "' to " + url + ": " + error);
				throw new RuntimeException("Database communication error: " + error);
			} else {
				//throw new RuntimeException("No success in field '" + jsonField + "' to " + url + ": " + result);
				throw new RuntimeException("Database communication error! URL: " + url + ", Result: " + result);
			}
		}
	}

	private String makeMatchQuery(HashMap<String, Object> filters, int from, int size) {
		StringWriter sw = new StringWriter();
		try {
			try (JsonGenerator g = factory.createGenerator(sw)) {
				startBooleanQuery(g, from, size);
				//add match filters
				for (Map.Entry<String, Object> entry : filters.entrySet()) {
					g.writeStartObject();
						g.writeObjectFieldStart("match");
							//TODO: temporary fix until we've updated the mapping
							/*
							g.writeObjectFieldStart(entry.getKey());
								g.writeObjectField("query", entry.getValue());
								g.writeStringField("analyzer", "simple");
							g.writeEndObject();
							*/
							g.writeObjectField(entry.getKey(), entry.getValue()); 		//<- as it should be
						g.writeEndObject();
					g.writeEndObject();
					//System.out.println(entry.getKey() + " = " + entry.getValue()); 		//debug
				}
				endBooleanQuery(g);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return sw.toString();
	}

}
