package net.b07z.sepia.server.teach.server;

import java.util.Properties;

import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.server.ConfigDefaults;
import net.b07z.sepia.server.core.tools.FilesAndStreams;
import net.b07z.sepia.server.teach.database.ConfigElasticSearch;
import net.b07z.sepia.server.teach.database.Elasticsearch;

/**
 * Configuration class for teach-API.
 *
 * @author Florian Quirin
 *
 */
public final class Config {
	public static final String SERVERNAME = "SEPIA-Teach-API"; 		//public server name
	public static String localName = "sepia-teach-server";			//**user defined local server name
	public static String localSecret = "123456";					//**user defined secret to validate local server
	public static final String apiVersion = "v2.0.0";				//API version
	
	//helper for dynamic class creation
	public static final String parentPackage = Config.class.getPackage().getName().substring(0, Config.class.getPackage().getName().lastIndexOf('.'));
	
	//Servers
	public static String configFile = "Xtensions/teach.properties";		//external configuration file
	public static int serverPort = 20722;								//**default server port
	public static final boolean enableCORS = true;						//enable CORS (set access-control headers)
	public static String endpointUrl = "http://localhost:20722/";		//**this API URL
	public static String assistAPI = "http://localhost:20721/";			//**link to Assistant-API
		
	//Shared key for internal communication
	public static String clusterKey = "KantbyW3YLh8jTQPs5uzt2SzbmXZyphW"; 	//**one step of inter-API communication security
	public static boolean allowInternalCalls = true;						//**allow calls between servers with shared secret auth.

	//Region settings
	public static final String REGION_US = "us";					//Region tag for US
	public static final String REGION_EU = "eu";					//Region tag for EU
	public static final String REGION_TEST = "us";					//Region tag for TEST
	public static final String REGION_CUSTOM = "custom";			//Region tag for CUSTOM server
	public static String defaultRegion = REGION_EU;				//**Region for different cloud services (e.g. AWS)

	public static boolean useDatabaseLog = false;

	//Default modules (implementations of certain interfaces)
	public static final String authenticationModule = ConfigDefaults.defaultAuthModule;
	public static final String teachDbModule = Elasticsearch.class.getCanonicalName();

	//some constants to keep track of the DB structure
	//indexes
	public static final String DB_STORAGE = "storage";			//unsorted data for later processing
	public static final String DB_KNOWLEDGE = "knowledge";		//processed and sorted data for queries
	public static final String DB_USERS = "users";				//user accounts
	public static final String DB_USERDATA = "userdata";		//user data like account, list, contacts, etc.
	public static final String DB_FEEDBACK = "storage";			//store feedback data. SAME as storage right now
	public static final String DB_COMMANDS = "commands";		//command teachings submitted by developers
	public static final String DB_ANSWERS = "answers";			//answers to chats and commands
	public static final String DB_LOGS = "logs";				//user actions log (write actions only)
	//types
	//... should be inside the corresponding classes e.g. Feedback/Command/... 

	//More stuff
	public static final String defaultClientInfo = ConfigDefaults.defaultClientInfo;	//in case the client does not submit the info use this.

	private Config() {
	}

	//----------helpers----------
	
	/**
	 * Load server settings from properties file. 
	 */
	public static void loadSettings(String confFile){
		if (confFile == null || confFile.isEmpty())	confFile = configFile;
		
		try{
			Properties settings = FilesAndStreams.loadSettings(confFile);
			//server
			endpointUrl = settings.getProperty("server_endpoint_url");
			assistAPI = settings.getProperty("server_assist_api_url");	
			serverPort = Integer.valueOf(settings.getProperty("server_port"));
			localName = settings.getProperty("server_local_name");
			localSecret = settings.getProperty("server_local_secret");
			clusterKey = settings.getProperty("cluster_key");
			//databases
			defaultRegion = settings.getProperty("db_default_region", "eu");
			ConfigElasticSearch.endpoint_custom = settings.getProperty("db_elastic_endpoint_custom", "");
			ConfigElasticSearch.endpoint_eu1 = settings.getProperty("db_elastic_endpoint_eu1");
			ConfigElasticSearch.endpoint_us1 = settings.getProperty("db_elastic_endpoint_us1");
			//more settings
			allowInternalCalls = Boolean.valueOf(settings.getProperty("allow_internal_calls"));
			useDatabaseLog = Boolean.valueOf(settings.getProperty("use_db_log"));
			
			LoggerFactory.getLogger(Config.class).info("loading settings from " + confFile + "... done.");
		}catch (Exception e){
			LoggerFactory.getLogger(Config.class).error("loading settings from " + confFile + "... failed!");
		}
	}
	/**
	 * Save server settings to file. Skip security relevant fields.
	 */
	public static void saveSettings(String confFile){
		if (confFile == null || confFile.isEmpty())	confFile = configFile;
		
		//save all personal parameters
		Properties config = new Properties();
		//server
		config.setProperty("server_endpoint_url", endpointUrl);
		config.setProperty("server_assist_api_url", assistAPI);
		config.setProperty("server_port", String.valueOf(serverPort));
		config.setProperty("server_local_name", localName);
		config.setProperty("server_local_secret", "");
		config.setProperty("cluster_key", "");
		//databases
		config.setProperty("db_default_region", defaultRegion);
		config.setProperty("db_elastic_endpoint_custom", ConfigElasticSearch.endpoint_custom);
		config.setProperty("db_elastic_endpoint_eu1", ConfigElasticSearch.endpoint_eu1);
		config.setProperty("db_elastic_endpoint_us1", ConfigElasticSearch.endpoint_us1);
		//more settings
		config.setProperty("allow_internal_calls", String.valueOf(allowInternalCalls));
		config.setProperty("use_db_log", String.valueOf(useDatabaseLog));
		
		try{
			FilesAndStreams.saveSettings(confFile, config);
			LoggerFactory.getLogger(Config.class).info("saving settings to " + confFile + "... done.");
		}catch (Exception e){
			LoggerFactory.getLogger(Config.class).error("saving settings to " + confFile + "... failed!");
		}
	}
	
}
