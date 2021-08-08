package net.b07z.sepia.server.teach.server;

import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.secure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.data.Answer;
import net.b07z.sepia.server.core.data.CmdMap;
import net.b07z.sepia.server.core.data.Command;
import net.b07z.sepia.server.core.data.Defaults;
import net.b07z.sepia.server.core.data.Language;
import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.data.SentenceBuilder;
import net.b07z.sepia.server.core.endpoints.CoreEndpoints;
import net.b07z.sepia.server.core.server.ConfigDefaults;
import net.b07z.sepia.server.core.server.RequestGetOrFormParameters;
import net.b07z.sepia.server.core.server.RequestParameters;
import net.b07z.sepia.server.core.server.SparkJavaFw;
import net.b07z.sepia.server.core.server.Validate;
import net.b07z.sepia.server.core.tools.ClassBuilder;
import net.b07z.sepia.server.core.tools.Connectors;
import net.b07z.sepia.server.core.tools.Converters;
import net.b07z.sepia.server.core.tools.DateTime;
import net.b07z.sepia.server.core.tools.Debugger;
import net.b07z.sepia.server.core.tools.Is;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.tools.Timer;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.server.teach.database.TeachDatabase;
import net.b07z.sepia.server.teach.database.TeachUiDataLoader;
import net.b07z.sepia.server.teach.data.Vote;
import net.b07z.sepia.server.teach.database.ElasticsearchLogger;
import net.b07z.sepia.server.teach.database.Feedback;
import spark.Request;
import spark.Response;

public final class Start {

	private static final Logger log = LoggerFactory.getLogger(Start.class);
	private static String startGMT = "";
	
	private static TeachDatabase db = null;
	private static TeachDatabase getDatabase(){
		return db == null ? (TeachDatabase) ClassBuilder.construct(Config.teachDbModule) : db;
	}
	
	public static final String LIVE_SERVER = "live";
	public static final String TEST_SERVER = "test";
	public static final String CUSTOM_SERVER = "custom";
	public static String serverType = "";
	
	public static boolean isSSL = false;
	private static String keystorePwd = "13371337";

	private Start() {}

	public static void main(String[] args) {
		//load settings
		serverType = TEST_SERVER;
		for (String arg : args){
			if (arg.equals("--test")){
				//Test system
				serverType = TEST_SERVER;
			}else if (arg.equals("--live")){
				//Local test system
				serverType = LIVE_SERVER;
			}else if (arg.equals("--my") || arg.equals("--custom")){
				//Custom system
				serverType = CUSTOM_SERVER;
			}else if (arg.equals("--local")){
				//Local test system
				serverType = "local";
			}else if (arg.equals("--ssl")){
				//SSL
				isSSL = true;
			}else if (arg.startsWith("keystorePwd=")){
				//Java key-store password - TODO: maybe not the best way to load the pwd ...
				keystorePwd = arg.replaceFirst(".*?=", "").trim();
			}
		}
		//set security
		if (isSSL){
			secure("Xtensions/SSL/ssl-keystore.jks", keystorePwd, null, null);
		}
		//load settings
		if (serverType.equals(TEST_SERVER)){
			log.info("--- Running " + Config.SERVERNAME + " with TEST settings ---");
			Config.configFile = "Xtensions/teach.test.properties";		
		}else if (serverType.equals(LIVE_SERVER)){
			log.info("--- Running " + Config.SERVERNAME + " with LIVE settings ---");
			Config.configFile = "Xtensions/teach.properties";
		}else if (serverType.equals(CUSTOM_SERVER)){
			log.info("--- Running " + Config.SERVERNAME + " with CUSTOM settings ---");
			Config.configFile = "Xtensions/teach.custom.properties";
		}
		Config.loadSettings(Config.configFile);
		
		//SETUP CORE-TOOLS
		JSONObject coreToolsConfig;
		//part 1
		coreToolsConfig = JSON.make(
				"defaultAssistAPI", Config.assistAPI,
				"defaultTeachAPI", Config.endpointUrl,
				"clusterKey", Config.clusterKey,
				"privacyPolicy", Config.privacyPolicyLink
		);
		ConfigDefaults.setupCoreTools(coreToolsConfig);
		//part 2
		long clusterTic = Timer.tic();
		JSONObject assistApiClusterData = ConfigDefaults.getAssistantClusterData();
		if (assistApiClusterData == null){
			throw new RuntimeException("Core-tools are NOT set properly! AssistAPI could not be reached!");
		}else{
			log.info("Received cluster-data from AssistAPI after " + Timer.toc(clusterTic) + "ms");
		}
		coreToolsConfig = JSON.make(
				"defaultAssistantUserId", JSON.getString(assistApiClusterData, "assistantUserId")
		);
		//common micro-services API-Keys
		//...JSON.put(coreToolsConfig, "...ApiKey", ...);
		ConfigDefaults.setupCoreTools(coreToolsConfig);
		
		//Check core-tools settings
		if (!ConfigDefaults.areCoreToolsSet()){
			throw new RuntimeException("Core-tools are NOT set properly!");
		}

		Debugger.println("Starting Teach-API server " + Config.apiVersion + " (" + serverType + ")", 3);
		startGMT = DateTime.getGMT(new Date(), "dd.MM.yyyy' - 'HH:mm:ss' - GMT'");
		Debugger.println("Date: " + startGMT, 3);
		
		//int maxThreads = 8;
		//threadPool(maxThreads);
		try {
			port(Integer.valueOf(System.getenv("PORT")));
			Debugger.println("Server running on port: " + Integer.valueOf(System.getenv("PORT")), 3);
		}catch (Exception e){
			int port = Config.serverPort;
			port(port);
			Debugger.println("Server running on port " + port, 3);
		}
		
		//set access-control headers to enable CORS
		if (Config.enableCORS){
			SparkJavaFw.enableCORS("*", "*", "*");
		}

		//Authenticate user and store basic account info
		/*
		before((request, response) -> {
			authenticate(request, response);
		});
		*/
		
		get("/online", (request, response) -> 	CoreEndpoints.onlineCheck(request, response));
		get("/ping", (request, response) -> 	CoreEndpoints.ping(request, response, Config.SERVERNAME));
		get("/validate", (request, response) -> CoreEndpoints.validateServer(request, response, Config.SERVERNAME, 
													Config.apiVersion, Config.localName, Config.localSecret));
		post("/hello", Start::helloWorld);

		post("/getTeachUiServices", Start::getTeachUiServices);

		post("/getCustomCommandMappings", Start::getCustomCommandMappings);
		post("/setCustomCommandMappings", Start::setCustomCommandMappings);
		
		post("/getPersonalCommands", Start::getPersonalCommands);
		post("/getAllPersonalCommands", Start::getAllPersonalCommands);
		post("/getAllCustomAssistantCommands", Start::getAllCustomAssistantCommands);
		post("/getPersonalCommandsByIds", Start::getPersonalCommandsByIds);
		post("/deletePersonalCommand", Start::deletePersonalCommand);
		post("/submitPersonalCommand", Start::submitPersonalCommand);
		// e.g. /submitPersonalCommand?language=en&sentence=This is the command&command=search&public=yes&reply=reply one&reply=reply two&KEY=...'

		post("/getAllCustomSentencesAsTrainingData", Start::getAllCustomSentencesAsTrainingData);
		
		post("/addSentence", Start::addSentence);	// To add a variation that is not a direct translation of an existing sentence.
		// e.g. /addSentence?id=ABCD&language=de&text=new sentenceKEY=...
		post("/voteSentence", Start::voteSentence);
		// e.g. /voteSentence?id=ABC12345&vote=positive|negative&text=This is the sentence&language=en&KEY=...'
		
		post("/feedback", Start::feedback);

		//TODO: add more tests for answers!
		post("/addAnswer", Start::addAnswer);
		post("/deleteAnswerById", Start::deleteAnswerById);
		post("/modifyAnswer", Start::modifyAnswer);
		post("/getAnswersByType", Start::getAnswersByType);
		post("/voteAnswer", Start::voteAnswer);
		
		post("/getLogs", Start::getLogs);

		//Error handling
		SparkJavaFw.handleError();
		
		spark.Spark.awaitInitialization();
		Debugger.println("Initialization complete, lets go!", 3);
	}
	
	private static Account authenticate(RequestParameters params, Request request, Response response){
		//statistics a
		long tic = System.currentTimeMillis();
		Statistics.add_API_hit();
					
		Account userAccount = new Account();
		
		//check for intra-API call that does not require authentication again
		boolean isInternalCall = Config.allowInternalCalls && 
				Validate.validateInternalCall(request, params.getString("sKey"), Config.clusterKey); 
		if (isInternalCall){
			//user data must be submitted in this case 
			
			//TODO: this should not be sent in GET calls and maybe only with SSL!
			//TODO: we might need to add a white-list of endpoints that allow internal calls.
			//It also is a potential risk if someone hacks the secure key and uses any user ID he wants :-(
			
			String accountS = params.getString("userData");
			JSONObject accountJS = JSON.parseString(accountS);
			if (accountJS == null){
				log.warn("Invalid internal API call from " + request.ip());
				halt(SparkJavaFw.returnResult(request, response, "{\"result\":\"fail\",\"error\":\"401 not authorized - invalid userData\"}", 401));
			}else{
				//log.info("successful internal API call from " + request.ip()); 		//activate this?
				userAccount.importJSON(accountJS);
			}

		//else do database authentication
		}else if (!userAccount.authenticate(params)){
			haltWithAuthError(request, response);
		}
		request.attribute(Defaults.ACCOUNT_ATTR, userAccount); 	//Note: keep this for testing and role-checks
		
		//statistics b
		if (!isInternalCall){
			Statistics.add_API_hit_authenticated();
			Statistics.save_Auth_total_time(tic);
		}else{
			Statistics.add_API_hit_internal();
		}
		return userAccount;
	}

	private static void haltWithAuthError(Request request, Response response) {
		halt(SparkJavaFw.returnResult(request, response, "{\"result\":\"fail\",\"error\":\"401 not authorized\"}", 401));
	}
	
	//------------------ ENDPOINTS -------------------
	
	//hello and statistics end-point
	private static String helloWorld(Request request, Response response){
				
		//Test Authentication
		/*
		Account userAccount = request.attribute(ACCOUNT_ATTR);
		System.out.println("User ID: " + userAccount.getUserID());
		System.out.println("User Name Data: " + userAccount.userName);
		System.out.println("User Name Short: " + userAccount.userNameShort);
		System.out.println("Access Level: " + userAccount.getAccessLevel());
		System.out.println("Account Language: " + userAccount.language);
		System.out.println("User Roles: " + userAccount.userRoles.toString());
		*/
				
		//time now
		Date date = new Date();
		String nowGMT = DateTime.getGMT(date, "dd.MM.yyyy' - 'HH:mm:ss' - GMT'");
		
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
		
		//msg
		Account userAccount = authenticate(params, request, response);
		String reply;
		if (userAccount.hasRole(Role.developer.name())){
			//stats
			reply = "Hello World!"
					+ "<br><br>"
					+ "Stats:<br>" +
							"<br>api: " + Config.apiVersion +
							"<br>started: " + startGMT +
							"<br>now: " + nowGMT +
							"<br>host: " + request.host() +
							"<br>url: " + request.url() + "<br><br>" +
							Statistics.getInfo();
		}else{
			reply = "Hello World!";
		}
		JSONObject msg = new JSONObject();
		JSON.add(msg, "result", "success");
		JSON.add(msg, "reply", reply);
		return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
	}
	
	//-- Teach-UI DATA --
	
	static String getTeachUiServices(Request request, Response response) {
		//we could use an account-dependent list of services
		
		//RequestParameters params = new RequestGetOrFormParameters(request);
		//Account account = authenticate(params, request, response);
		long tic = System.currentTimeMillis();
		
		String servicesJson;
		try{
			servicesJson = TeachUiDataLoader.getServices(null);		//NOTE: add account here?
			
			//statistics
			Statistics.addOtherApiHit("getTeachUiServices");
	      	Statistics.addOtherApiTime("getTeachUiServices", tic);
	      	
			JSONObject msg = new JSONObject();
			JSON.add(msg, "result", servicesJson);
			return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
			
		}catch (Exception e){
			//statistics
			Statistics.addOtherApiHit("getTeachUiServices ERROR");
	      	Statistics.addOtherApiTime("getTeachUiServices ERROR", tic);
	      	
			Debugger.printStackTrace(e, 3);
			JSONObject msg = new JSONObject();
			JSON.add(msg, "result", "fail");
			JSON.add(msg, "error", "Could not load data, check teach-server logs for more info.");
			return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
		}		
	}
	
	//-- COMMAND MAPPINGS (CMD -> SERVICE) --
	
	static String getCustomCommandMappings(Request request, Response response) {
		long tic = System.currentTimeMillis();
		
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
		
		//get account
		Account account = authenticate(params, request, response);
				
		String customOrSystem = getOrDefault("customOrSystem", CmdMap.CUSTOM, params);
		String userId = account.getUserID();
		
		TeachDatabase db = getDatabase();
		List<CmdMap> map = db.getCustomCommandMappings(userId, customOrSystem, null);
		
		JSONObject msg = new JSONObject();
		JSONArray data = new JSONArray();  
		for (CmdMap cm : map){
			JSON.add(data, cm.getJSON());
		}
		JSON.add(msg, "result", data);
		
		//statistics
		Statistics.addOtherApiHit("getCustomCommandMappings");
      	Statistics.addOtherApiTime("getCustomCommandMappings", tic);
				
		return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
	}
	
	static String setCustomCommandMappings(Request request, Response response){
		long tic = System.currentTimeMillis();
		
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
		
		//get account
		Account account = authenticate(params, request, response);
				
		String customOrSystem = getOrDefault("customOrSystem", CmdMap.CUSTOM, params);
		boolean overwrite = getOrDefault("overwrite", false, params);
		String userId = account.getUserID();
		String mapArrayAsString = getOrDefault("mappings", "", params);
		if (mapArrayAsString.isEmpty()){
			throw new RuntimeException("required parameter 'mappings' is missing or empty!");
		}
		Set<CmdMap> cmSet;
		try{
			JSONArray mapArray = JSON.parseStringToArrayOrFail(mapArrayAsString);
			cmSet = new HashSet<>();
			for (Object o : mapArray){
				cmSet.add(new CmdMap((JSONObject) o));
			}
		}catch (Exception e){
			throw new RuntimeException("parsing parameter 'mappings' failed with error: " + e.getMessage());
		}
		
		HashMap<String, Object> filters = new HashMap<>();
		filters.put("overwrite", overwrite);
		
		TeachDatabase db = getDatabase();
		db.setCustomCommandMappings(userId, customOrSystem, cmSet, filters);
		
		//statistics
		Statistics.addOtherApiHit("setCustomCommandMappings");
      	Statistics.addOtherApiTime("setCustomCommandMappings", tic);
		
		return SparkJavaFw.sendSuccessResponse(request, response);
	}
		
	//-- COMMANDS --

	/**
	 * Checks for duplicates when "overwriteExisting" is set, otherwise it assumes the user only ends up here
	 * if there's no similar sentence already.
	 */
	static String submitPersonalCommand(Request request, Response response) {
		//statistics a
		long tic = System.currentTimeMillis();
		
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
				
		//get account
		Account account = authenticate(params, request, response);
		
		//get required parameters:
		Language language = getLanguageOrFail(params);
		String environment = getOrDefault("environment", "all", params);
		String deviceId = params.getString("device_id");
		String sentence = getOrFail("sentence", params);
		String command = getOrFail("command", params);
		String publicStr = getOrFail("public", params);
		if (!(publicStr.equals("no") || publicStr.equals("yes"))) {
			throw new RuntimeException("required parameter 'public' must be 'yes' or 'no')");
		}
		boolean isPublic = publicStr.equals("yes");
		String localStr = getOrFail("local", params);
		if (!(localStr.equals("no") || localStr.equals("yes"))) {
			throw new RuntimeException("required parameter 'local' must be 'yes' or 'no')");
		}
		boolean isLocal = localStr.equals("yes");
		String explicitStr = getOrDefault("explicit", "no", params);
		boolean isExplicit = explicitStr.equals("yes");
		
		boolean overwriteExisting = getOrDefault("overwriteExisting", false, params);
		
		//get optional parameters:
		String taggedSentence = params.getString("tagged_sentence");
		JSONObject paramsJson = JSON.parseString(params.getString("params"));
		String cmdSummary = params.getString("cmd_summary");
		if ((cmdSummary == null || cmdSummary.isEmpty()) && (paramsJson != null && !paramsJson.isEmpty())){
			cmdSummary = Converters.makeCommandSummary(command, paramsJson);
		}
		String userLocation = params.getString("user_location");		//TODO: The client should set this as detailed or vague as required
		String[] repliesArr = params.getStringArray("reply");
		List<String> replies = repliesArr == null ? new ArrayList<>() : Arrays.asList(repliesArr);
		//custom button data and stuff
		JSONObject dataJson;		
		String dataJsonString = params.getString("data");
		if (Is.notNullOrEmpty(dataJsonString)){
			dataJson = JSON.parseString(dataJsonString); 		
		}else{
			dataJson = new JSONObject(); 		//NOTE: If no data is submitted it will kill all previous data info (anyway the whole object is overwritten)
		}
		
		//build sentence - Note: Commands support sentence arrays but we use only one entry
		List<Command.Sentence> sentenceList = new ArrayList<>();
		Command.Sentence sentenceObj = new SentenceBuilder(sentence, account.getUserID(), "community") 		//TODO: add user role check to switch from "community" to "developer"?
				.setLanguage(Language.valueOf(language.name().toUpperCase()))
				.setParams(paramsJson)
				.setCmdSummary(cmdSummary)
				.setTaggedText(taggedSentence)
				.setPublic(isPublic)
				.setLocal(isLocal)
				.setExplicit(isExplicit)
				.setEnvironment(environment)
				.setDeviceId(deviceId)
				.setUserLocation(userLocation)
				.setData(dataJson)
				//TODO: keep it or remove it? The general answers should be stored in an index called "answers"
				//and the connector is the command. For chats, custom answers are inside parameter "reply". But I think its still useful here ...
				.setReplies(new ArrayList<>(replies))
				.build();
		sentenceList.add(sentenceObj);

		//build command
		Command cmd = new Command(command);
		cmd.add(sentenceList);
		//System.out.println(cmd.toJson()); 		//debug
		
		//submit to DB
		TeachDatabase db = getDatabase();
		
		//get ID if sentence exists
		if (overwriteExisting){
			//search existing:
			String itemId = "";
			if (taggedSentence != null && !taggedSentence.isEmpty()){
				itemId = db.getIdOfCommand(account.getUserID(), language.toValue(), taggedSentence);
			}else if (!sentence.isEmpty()){
				itemId = db.getIdOfCommand(account.getUserID(), language.toValue(), sentence);
			}
			if (itemId == null || itemId.isEmpty()){
				//not found
				db.submitCommand(cmd);
			}else{
				//overwrite
				db.submitCommand(cmd, itemId);
			}
		}else{
			db.submitCommand(cmd);
		}
		
		//log
		logDB(request, "submitted command");
		
		//statistics b
		Statistics.add_teach_hit();
		Statistics.save_teach_total_time(tic);
		
		//answer to client
		return SparkJavaFw.sendSuccessResponse(request, response);
	}
	
	static String getPersonalCommands(Request request, Response response) {
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
		
		Account userAccount = authenticate(params, request, response);
		//statistics a
		long tic = System.currentTimeMillis();
		
		Language language = getLanguageOrFail(params);
		boolean includePublic = getOrDefault("include_public", true, params); 	//default is with public now
		String searchText = getOrDefault("searchText", "", params);				//in case we only want certain results matching the search text
		HashMap<String, Object> filters = new HashMap<>();
		//String userOrSelf = getOrDefault("user", userAccount.getUserID(), request); 
		//note: list function for user has been removed here since the assist-API has its own version now
		//filters.put("userIds", userOrSelf);
		filters.put("userIds", userAccount.getUserID());
		filters.put("language", language.name());
		filters.put("includePublic", includePublic);
		filters.put("searchText", searchText);
		JSONArray output = getDatabase().getPersonalCommands(filters);
		JSONObject msg = new JSONObject();
		JSON.add(msg, "result", output);
		
		//statistics b
		Statistics.addOtherApiHit("getPersonalCommands");
      	Statistics.addOtherApiTime("getPersonalCommands", tic);
		
		return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
	}
	
	static String getAllPersonalCommands(Request request, Response response){
		//statistics a
		long tic = System.currentTimeMillis();
		
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
		
		Account userAccount = authenticate(params, request, response);
		String userId = userAccount.getUserID();
		if (userId == null || userId.isEmpty()){
			throw new RuntimeException("Cannot load commands, userId is invalid!");
		}
				
		JSONArray output = getSpecificPersonalCommands(userAccount.getUserID(), userAccount.getPreferredLanguage(), params);
		JSONObject msg = new JSONObject();
		JSON.add(msg, "result", output);
		
		//statistics b
		Statistics.addOtherApiHit("getAllPersonalCommands");
      	Statistics.addOtherApiTime("getAllPersonalCommands", tic);
		
		return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
	}
	static String getAllCustomAssistantCommands(Request request, Response response){
		//statistics a
		long tic = System.currentTimeMillis();
		
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
		
		Account userAccount = authenticate(params, request, response);
		String userId = userAccount.getUserID();
		if (userId == null || userId.isEmpty()){
			throw new RuntimeException("Cannot load commands, userId is invalid!");
		}
		if (userId.equals(ConfigDefaults.defaultAssistantUserId)){
			throw new RuntimeException("User ID and assistant ID are identical. Use 'getAllPersonalCommands' instead!");
		}
		
		String language = userAccount.getPreferredLanguage();		//NOTE: by default it will use USER language, but can be overwritten via "language" parameter
		JSONArray output = getSpecificPersonalCommands(ConfigDefaults.defaultAssistantUserId, language, params);
		JSONObject msg = new JSONObject();
		JSON.add(msg, "result", output);
		
		//statistics b
		Statistics.addOtherApiHit("getAllCustomAssistantCommands");
      	Statistics.addOtherApiTime("getAllCustomAssistantCommands", tic);
		
		return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
	}
	private static JSONArray getSpecificPersonalCommands(String userId, String userLanguage, RequestParameters params){
		String language = getOrDefault("language", userLanguage, params);
		String from = getOrDefault("from", "0", params);
		String size = getOrDefault("size", "10", params);
		String with_button_only = getOrDefault("button", null, params);
		boolean sortByDateNewest = getOrDefault("sortByDate", false, params);
		
		HashMap<String, Object> filters = new HashMap<>();
		filters.put("userId", userId);
		filters.put("language", language);
		filters.put("from", from);
		filters.put("size", size);
		if (with_button_only != null){
			filters.put("button", true); 	//Its either true or not included
		}
		filters.put("sortByDate", sortByDateNewest);
		
		TeachDatabase db = getDatabase();
		JSONArray output = db.getAllPersonalCommands(filters);
		return output;
	}
	
	static String getPersonalCommandsByIds(Request request, Response response) {
		//statistics a
		long tic = System.currentTimeMillis();
		
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
		
		Account userAccount = authenticate(params, request, response);
		JSONArray ids = params.getJsonArray("ids");
		if (Is.nullOrEmpty(ids)){
			throw new RuntimeException("Missing or empty 'ids' parameter");
		}
		HashMap<String, Object> filters = new HashMap<>();
		filters.put("userId", userAccount.getUserID());		//to make sure that this can only be used by the authenticated user
		JSONArray output = getDatabase().getPersonalCommandsByIds(Converters.jsonArrayToStringList(ids), filters);
		JSONObject msg = new JSONObject();
		JSON.add(msg, "result", output);
		
		//statistics b
		Statistics.addOtherApiHit("getPersonalCommandsByIds");
      	Statistics.addOtherApiTime("getPersonalCommandsByIds", tic);
		
		return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
	}
	
	static String deletePersonalCommand(Request request, Response response) {
		long tic = Debugger.tic();
		
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);

		String id = getOrFail("id", params);
		Account userAccount = authenticate(params, request, response);
		String userId = userAccount.getUserID();
		if (userId == null || userId.isEmpty()){
			throw new RuntimeException("Cannot delete command, userId is invalid!");
		}
		
		TeachDatabase db = getDatabase();
		JSONObject res = db.deleteCommand(id, userId);
		//JSON.printJSONpretty(res); 		//DEBUG
		JSONObject msg;
		if (Connectors.httpSuccess(res)){
			long deleted = JSON.getLongOrDefault(res, "deleted", 0);
			//if (deleted > 0){	} 	//log it?
			msg = JSON.make("result", JSON.make("deleted", deleted));
			
			//statistics
			logDB(request, "deleted personal command with id: " + id);
			Statistics.addOtherApiHit("deleteCommandFromDB");
	      	Statistics.addOtherApiTime("deleteCommandFromDB", tic);
			
		}else{
			msg = JSON.make("result", "fail");
			Statistics.addOtherApiHit("deleteCommandFromDB ERROR");
	      	Statistics.addOtherApiTime("deleteCommandFromDB ERROR", tic);
		}
      	
      	return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
	}
	
	//-- SENTENCES (of commands) --
	
	static String getAllCustomSentencesAsTrainingData(Request request, Response response) {
		//allow request?
		RequestParameters params = new RequestGetOrFormParameters(request);
		authenticate(params, request, response);
		requireRole(request, Role.superuser);
		
		Language language = getLanguageOrFail(params);
		JSONArray sentencesForTraining = getDatabase().getAllCustomSentencesAsTrainingData(language.toValue());
		
		return SparkJavaFw.returnResult(request, response, JSON.make(
				"result", JSON.make(
						"sentences", sentencesForTraining
				)
		).toJSONString(), 200);
	}

	static String addSentence(Request request, Response response) {
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
				
		Account userAccount = authenticate(params, request, response);
		requireTranslatorRole(request);
		
		String id = getOrFail("id", params);
		Language language = getLanguageOrFail(params);
		String text = getOrFail("text", params);
		getDatabase().addSentence(id, language, text, userAccount);
		logDB(request, "added sentence", language, null, text, getDatabase());
		return SparkJavaFw.sendSuccessResponse(request, response);
	}
	
	static String voteSentence(Request request, Response response) {
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
		
		Account userAccount = authenticate(params, request, response);
		
		String docId = getOrFail("id", params);
		Language votedLanguage = getLanguageOrFail(params);
		String votedSentence = getOrFail("text", params);
		TeachDatabase db = getDatabase();
		Vote vote = Vote.valueOf(getOrFail("vote", params));
		db.voteSentence(docId, votedSentence, votedLanguage, vote, userAccount);
		JSONObject msg = new JSONObject();
		JSON.add(msg, "result", "success");
		logDB(request, "voted sentence '" + votedSentence + "' as " + vote, votedLanguage, db);
		return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
	}

	//-- ANSWERS --
	
	static String getAnswersByType(Request request, Response response) {
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
				
		Account userAccount = authenticate(params, request, response);
		String userOrSelf = getOrDefault("user", userAccount.getUserID(), params); //note: user can be given as a list here, e.g. "assistant,test@assistant.com"
		
		//require role when requested user is not user who asks
		if (!userOrSelf.toLowerCase().equals(userAccount.getUserID().toLowerCase())){
			requireSeniorDeveloperRole(request);
			//TODO: add an "all" tag to get answers of all users? or leave that to the "browser" for now?
		}
		String answerType = getOrFail("type", params);
		String languageOrNull = getOrDefault("language", null, params);
		JSONObject json = getDatabase()
				.getAnswersByType(Config.DB_ANSWERS, Answer.ANSWERS_TYPE, answerType, languageOrNull, userOrSelf);
		return SparkJavaFw.returnResult(request, response, json.toJSONString(), 200);
	}
	
	static String addAnswer(Request request, Response response) {
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
		
		//TODO: we should allow every user to add and edit answers for himself, by default the assistant reads only Defaults.USER answers
		Account userAccount = authenticate(params, request, response);
		requireDeveloperRole(request);
		
		Language language = getLanguageOrFail(params);
		String type = getOrFail("type", params);
		String text = getOrFail("text", params);
		List<Answer.Character> characters = new ArrayList<>();
		if (getOrDefault("neutral", false, params)) {
			characters.add(Answer.Character.neutral);
		}
		if (getOrDefault("cool", false, params)) {
			characters.add(Answer.Character.cool);
		}
		if (getOrDefault("polite", false, params)) {
			characters.add(Answer.Character.polite);
		}
		if (getOrDefault("rude", false, params)) {
			characters.add(Answer.Character.rude);
		}
		int repetition = Integer.parseInt(getOrFail("repetition", params));
		int mood = Integer.parseInt(getOrFail("mood", params));
		String source = "assistant-tools";
		boolean isPublic = getOrDefault("public", true, params);
		boolean isLocal = getOrDefault("local", false, params);
		boolean isExplicit = getOrDefault("explicit", false, params);
		String[] tagsArray = getOrDefault("tags", "", params).split(",\\s*");
		List<String> tags = Arrays.asList(tagsArray).stream().filter(f -> !f.isEmpty()).collect(Collectors.toList());
		//check if the answer should be saved as a default system answer
		boolean makeSystemDefault = getOrDefault("systemdefault", false, params);
		String savedUser = userAccount.getUserID();
		if (makeSystemDefault){
			 requireSeniorDeveloperRole(request);
			 savedUser = ConfigDefaults.defaultAssistantUserId;
		}
		Answer answer = new Answer(language, type, text, characters, repetition, mood, savedUser, source,
				                       isPublic, isLocal, isExplicit, null, false, null, "", tags, null);
		getDatabase().addAnswer(answer, userAccount);
		logDB(request, "added answer", language, null, text, getDatabase());
		return SparkJavaFw.sendSuccessResponse(request, response);
	}
	
	static String deleteAnswerById(Request request, Response response) {
		long tic = Debugger.tic();
		
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);

		String id = getOrFail("id", params);
		Account userAccount = authenticate(params, request, response);
		String userId = userAccount.getUserID();
		if (userId == null || userId.isEmpty()){
			throw new RuntimeException("Cannot delete answer, userId is invalid!");
		}
		JSONObject msg;
		
		//get document to check user
		TeachDatabase db = getDatabase();
		JSONObject answerRes = db.getAnswerById(id);
		boolean found = JSON.getBoolean(answerRes, "found");
		if (!found){
			msg = JSON.make("result", "fail", "error", "ID not found!");
			return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
		}
		JSONObject answerResSource = JSON.getJObject(answerRes, "_source");
		//check user
		String foundUser = JSON.getString(answerResSource, "user");
		if (foundUser == null || foundUser.isEmpty()){
			log.warn("deleteAnswerById - ID '" + id + "' has invalid data! Needs clean-up!");
			Statistics.addOtherApiHit("deleteAnswerById ERROR");
	      	Statistics.addOtherApiTime("deleteAnswerById ERROR", tic);
	      	
			msg = JSON.make("result", "fail", "error", "ID has invalid data!");
			return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
		
		}else if (foundUser.equals(userId)){
			//clear for deletion - every user can delete his own answers
			JSONObject result = db.deleteAnswerById(id, userId);
			msg = JSON.make("result", "success", "info", result);
			
			logDB(request, "deleted answer with id: " + id);
			Statistics.addOtherApiHit("deleteAnswerById");
	      	Statistics.addOtherApiTime("deleteAnswerById", tic);
			return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
		
		}else{
			//check user role
			requireSeniorDeveloperRole(request);
			
			//clear for deletion
			JSONObject result = db.deleteAnswerById(id, foundUser);
			msg = JSON.make("result", "success", "info", result);
			
			logDB(request, "deleted answer with id: " + id);
			Statistics.addOtherApiHit("deleteAnswerById");
	      	Statistics.addOtherApiTime("deleteAnswerById", tic);
			return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
		}
	}
	
	static String modifyAnswer(Request request, Response response) {
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
		
		authenticate(params, request, response);
		requireTranslatorRole(request);
		
		String id = getOrFail("id", params);
		Language language = getLanguageOrFail(params);
		String oldText = getOrFail("oldText", params);
		String newText = getOrFail("newText", params);
		getDatabase().modifyAnswer(id, language, oldText, newText);
		logDB(request, "modify answer", language, oldText, newText, getDatabase());
		return SparkJavaFw.sendSuccessResponse(request, response);
	}
	
	static String voteAnswer(Request request, Response response) {
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
				
		Account userAccount = authenticate(params, request, response);
		String docId = getOrFail("id", params);
		// id would be enough, but just to be sure (and to be similar to voteSentence), we also check text and language:
		Language votedLanguage = getLanguageOrFail(params);
		String votedSentence = getOrFail("text", params);
		TeachDatabase db = getDatabase();
		Vote vote = Vote.valueOf(getOrFail("vote", params));
		db.voteAnswer(docId, votedSentence, votedLanguage, vote, userAccount);
		JSONObject msg = new JSONObject();
		JSON.add(msg, "result", "success");
		logDB(request, "voted answer '" + votedSentence + "' as " + vote, votedLanguage, db);
		return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
	}
	
	//--FEEDBACK--
	
	static String feedback(Request request, Response response) {
		
		//statistics a
		long tic = System.currentTimeMillis();
		
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
		
		//get account
		Account account = authenticate(params, request, response);
		//TODO: add role check to see if user is allowed to retrieve feedback or write only
		
		//get action (submit, retrieve)
		String action = params.getString("action");
				
		//no action
		if (action == null || action.trim().isEmpty()){
			JSONObject msg = new JSONObject();
			JSON.add(msg, "result", "fail");
			JSON.add(msg, "error", "action attribute missing or invalid! Use e.g. submit, retrieve.");
			return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
		}
		
		//get default database for feedback data
		TeachDatabase db = getDatabase();
		
		//submit feedback
		if (action.trim().equals("submit")){
			
			//info (like, report, deprecated: dislike)
			String info = params.getString("info");
			String dataStr = params.getString("data");
			JSONObject data = JSON.parseString(dataStr);
			
			//no info?
			if (info == null || info.trim().isEmpty()){
				JSONObject msg = new JSONObject();
				JSON.add(msg, "result", "fail");
				JSON.add(msg, "error", "info attribute missing or invalid! Use e.g. like, report.");
				return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
			}
			//no data?
			if (data == null || data.isEmpty()){
				JSONObject msg = new JSONObject();
				JSON.add(msg, "result", "fail");
				JSON.add(msg, "error", "data attribute missing or invalid!");
				return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
			}else{
				//add user id, time stamp and info
				JSON.add(data, "user", account.getUserID());
				JSON.add(data, "timestamp", System.currentTimeMillis());
				if (!data.containsKey("info")){
					JSON.add(data, "info", info);
				}
			}
			
			//make id from cleaned text
			String itemId = JSON.getString(data, "text");
			if (itemId.isEmpty()){
				//make id of user and time-stamp
				itemId = account.getUserID() + "_" + System.currentTimeMillis(); 
			}else{
				itemId = Converters.makeIDfromSentence(itemId);
			}
			
			//check
			if (itemId.isEmpty()){
				String msg = "{\"result\":\"fail\",\"error\":\"no valid data\"}";
				return SparkJavaFw.returnResult(request, response, msg, 200);
			}
			
			if (info.equals("like")){
				Feedback.saveAsync(db, Feedback.INDEX, Feedback.TYPE_LIKE, itemId, data);	 	//set and forget ^^
				//System.out.println("DB SENT: " + Feedback.INDEX + "/" + Feedback.TYPE_LIKE + "/" + item_id + " - Data: " + data.toJSONString()); 		//debug
			}
			else if (info.equals("dislike")){
				Feedback.saveAsync(db, Feedback.INDEX, Feedback.TYPE_DISLIKE, itemId, data);	 //set and forget ^^
				//System.out.println("DB SENT: " + Feedback.INDEX + "/" + Feedback.TYPE_DISLIKE + "/" + item_id + " - Data: " + data.toJSONString()); 		//debug
			}
			else if (info.equals("report")){
				Feedback.saveAsync(db, Feedback.INDEX, Feedback.TYPE_REPORT, itemId, data);	 //set and forget ^^
				//System.out.println("DB SENT: " + Feedback.INDEX + "/" + Feedback.TYPE_REPORT + "/" + item_id + " - Data: " + data.toJSONString()); 		//debug
			}
			else if (info.equals("nps")){
				//we can also use "lang" and "client" parameters to get more details
				log.info("NPS - " + "id: " + account.getUserID() + " - score: " + data.get("score") + " - comment: " + data.get("comment") + " - TS: " + System.currentTimeMillis());
			}
			else{
				//invalid info
				JSONObject msg = new JSONObject();
				JSON.add(msg, "result", "fail");
				JSON.add(msg, "error", "info attribute missing or invalid! Use e.g. like, report.");
				return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
			}
			
			//if you come till here that everything has been submitted :-) It might not be successful though as we don't wait for feedback
			JSONObject msg = new JSONObject();
			JSON.add(msg, "result", "processing");
			JSON.add(msg, "duration_ms", Timer.toc(tic));
			
			//statistics b
			Statistics.add_feedback_hit();
			Statistics.save_feedback_total_time(tic);
			
			return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
		}
		
		//get feedback
		else if (action.trim().equals("retrieve")){
			
			//some parameters to match
			String language = params.getString("language");
			String user = params.getString("user");
			String info = params.getString("info"); 		//like, report
			int from =Integer.parseInt(getOrFail("from", params));
			int size =Integer.parseInt(getOrFail("size", params));
			
			//no info?
			if (info == null || info.trim().isEmpty()){
				JSONObject msg = new JSONObject();
				JSON.add(msg, "result", "fail");
				JSON.add(msg, "error", "info attribute missing or invalid! Use e.g. like, dislike, report.");
				return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
			}
			
			//build match filters
			HashMap<String, Object> filters = new HashMap<>();
			if (language != null) filters.put("language", language);
			if (user != null) filters.put("user", user); 
			
			JSONArray feedback;
			if (info.equals("report")){
				feedback = db.getReportedFeedback(filters, from, size);
			}else if (info.equals("like")){
				feedback = db.getLikedFeedback(filters, from, size);
			}else{
				JSONObject msg = new JSONObject();
				JSON.add(msg, "result", "fail");
				JSON.add(msg, "error", "info attribute missing or invalid! Use e.g. like, dislike, report.");
				return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
			}
			JSONObject result = new JSONObject();
			JSON.add(result, "result", feedback);
			
			//statistics b
			Statistics.add_KDB_read_hit();
			Statistics.save_KDB_read_total_time(tic);
			
			return SparkJavaFw.returnResult(request, response, result.toJSONString(), 200);
		}
		
		//invalid request
		else{
			//no valid action
			JSONObject msg = new JSONObject();
			JSON.add(msg, "result", "fail");
			JSON.add(msg, "error", "action attributes missing or invalid! Use e.g. submit, retrieve.");
			return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
		}
	}
	
	//------------------------------------------------------------------------------
	
	private static void logDB(Request request, String message) {
		logDB(request, message, null, null, null, getDatabase());
	}
	private static void logDB(Request request, String message, Language language, TeachDatabase db) {
		logDB(request, message, language, null, null, db);
	}
	private static void logDB(Request request, String message, Language language, String oldValue, String newValue, TeachDatabase db) {
		Account userAccount = request.attribute(Defaults.ACCOUNT_ATTR);
		if (Config.useDatabaseLog){
			new ElasticsearchLogger(db).log(userAccount, message, language, oldValue, newValue);
		}else{
			String logInfo = "EVENT-LOG" 
					+ " - id: " + userAccount.getUserID() 
					+ " - msg: " + message;
			if (language != null){
				logInfo += (" - lang: " + language);
			}
			if (oldValue != null){
				logInfo += (" - old: " + oldValue);
			}
			if (newValue != null){
				logInfo += (" - new: " + newValue);
			}
			log.info(logInfo);
		}
	}
	static String getLogs(Request request, Response response) {
		//prepare parameters
		RequestParameters params = new RequestGetOrFormParameters(request);
				
		authenticate(params, request, response);
		requireSeniorDeveloperRole(request);
		
		String from = getOrFail("from", params);
		String size = getOrFail("size", params);
		JSONObject json = getDatabase().getLogs(Config.DB_LOGS, ElasticsearchLogger.LOGS_TYPE, Integer.parseInt(from), Integer.parseInt(size));
		return SparkJavaFw.returnResult(request, response, json.toJSONString(), 200);
	}

	//------------------------------------------------------------------------------
		
	/*
	private static Set<String> getSetOrFail(String paramName, Request request) {
		String[] values = request.queryParamsValues(paramName);
		if (values == null) {
			throw new RuntimeException("Missing '" + paramName + "' parameter");
		}
		Set<String> result = new HashSet<>();
		for (String value : values) {
			validate(paramName, value);
			result.add(value);
		}
		return result;
	}
	*/
	private static String getOrFail(String paramName, RequestParameters params) {
		String val = params.getString(paramName);
		validate(paramName, val);
		return val;
	}
	private static String getOrDefault(String paramName, String defaultValue, RequestParameters params) {
		String val = params.getString(paramName);
		if (val != null) {
			return val;
		} else {
			return defaultValue;
		}
	}
	private static boolean getOrDefault(String paramName, boolean defaultBoolean, RequestParameters params) {
		String val = params.getString(paramName);
		if (val != null) {
			return Boolean.parseBoolean(val) || val.equals("on");  // 'on' is what jquery gives for val() for turned-on checkboxes
		} else {
			return defaultBoolean;
		}
	}
	private static void validate(String paramName, String val) {
		if (val == null) {
			throw new RuntimeException("Missing '" + paramName + "' parameter");
		}
		if (val.trim().isEmpty()) {
			throw new RuntimeException("Parameter '" + paramName + "' is empty or whitespace only");
		}
	}
	private static Language getLanguageOrFail(RequestParameters params) {
		String code = getOrFail("language", params);
		return Language.forValue(code);
	}
	
	private static void requireTranslatorRole(Request request) {
		requireRole(request, Role.translator);
	}
	/*private static void requireTesterRole(Request request) {
		requireRole(request, Role.tester);
	}*/
	private static void requireDeveloperRole(Request request) {
		requireRole(request, Role.developer);
	}
	private static void requireSeniorDeveloperRole(Request request) {
		requireRole(request, Role.seniordev);
	}
	private static void requireRole(Request request, Role role) {
		String requiredRole = role.name();
		Account userAccount = request.attribute(Defaults.ACCOUNT_ATTR);
		if (!userAccount.hasRole(requiredRole)) {
			//TODO: now that we can assign roles in the JUnit test we could remove the authRequired and add roles to all tests as needed (and can check against missing roles as well)
			throw new RuntimeException("User '" + userAccount.getUserID() + "' doesn't have the required role '" + requiredRole + "'. User's roles: " + userAccount.getUserRoles());
		}
	}
}
