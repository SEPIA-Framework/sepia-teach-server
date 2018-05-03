package net.b07z.sepia.server.teach.database;

/**
 * Set some common configuration variables for ElasticSearch.
 *
 * @author Florian Quirin
 *
 */
public class ConfigElasticSearch {

	//EU-cluster
	public static String endpoint_eu1 = "";
	//US-cluster
	public static String endpoint_us1 = "";
	//Custom-cluster: running locally or on a custom server
	public static String endpoint_custom = "http://localhost:20724";

	public static String getEndpoint(String region){
		//EU
		if (region.startsWith("eu")){
			return endpoint_eu1;
		//US
		}else if (region.startsWith("us")){
			return endpoint_us1;
		//CUSTOM
		}else{
			return endpoint_custom;
		}
	}
}
