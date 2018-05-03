package net.b07z.sepia.server.teach.database;

import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.tools.Timer;
import net.b07z.sepia.server.teach.server.Config;
import net.b07z.sepia.server.teach.server.Statistics;

/**
 * Here you will find help methods to build up databases for feedback related stuff. 
 * 
 * @author Florian Quirin
 */
public final class Feedback {
	
	//some constants for the DB structure
	public static final String INDEX = Config.DB_FEEDBACK;			//unsorted data for later processing
	public static final String TYPE_LIKE = "feedback_likes";		//type: likes - good answer
	public static final String TYPE_DISLIKE = "feedback_dislikes";	//type: dislikes - bad or missing answer
	public static final String TYPE_REPORT = "feedback_reports";	//type: reported - inappropriate content

	private Feedback() {
	}

	//TODO: rewrite the asynchronous write methods to collect data and write all at the same time as batchWrite when finished collecting,
	//		because it might well be that there is a bunch of stuff ready to write to the same database in the end ...
	
	/**
	 * Save stuff to database without waiting for reply, making this save method UNSAVE so keep that in mind when using it.
	 * Errors get written to log.
	 * @param index - index or table name like e.g. "users" or "knowledge"
	 * @param type - subclass name, e.g. "account", "lists", "banking" (for users) or "geodata" and "dictionary" (for knowledge)
	 * @param item_id - unique item/id name, e.g. user email address, dictionary word or geodata location name
	 * @param data - JSON string with data objects that should be stored for index/type/item, e.g. {"name":"john"}
	 */
	public static void saveAsync(TeachDatabase db, String index, String type, String item_id, JSONObject data){
		Thread thread = new Thread(){
		    @Override
				public void run(){
		    	long tic = Timer.tic();
		    	db.setItemData(index, type, item_id, data);
					Statistics.add_KDB_write_hit();
					Statistics.save_KDB_write_total_time(tic);
		    }
		};
		thread.start();
	}
	
	/**
	 * Save stuff to database without waiting for reply, making this save method UNSAVE so keep that in mind when using it.
	 * Errors get written to log. This method does not require an ID, it is auto-generated.
	 * @param index - index or table name like e.g. "users" or "knowledge"
	 * @param type - subclass name, e.g. "account", "lists", "banking" (for users) or "geodata" and "dictionary" (for knowledge)
	 * @param data - JSON string with data objects that should be stored for index/type/item, e.g. {"name":"john"}
	 */
	public static void saveAsyncAnyID(TeachDatabase db, String index, String type, JSONObject data){
		Thread thread = new Thread(){
			@Override
			public void run(){
				long tic = Timer.tic();
				db.setAnyItemData(index, type, data);
				//Debugger.println("KNOWLEDGE DB UPDATED! - PATH: " + index + "/" + type + "/[rnd] - TIME: " + System.currentTimeMillis(), 1);
				Statistics.add_KDB_write_hit();
				Statistics.save_KDB_write_total_time(tic);
			}
		};
		thread.start();
	}

}
