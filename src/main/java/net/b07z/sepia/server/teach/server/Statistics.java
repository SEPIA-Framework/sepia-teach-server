package net.b07z.sepia.server.teach.server;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.b07z.sepia.server.core.server.BasicStatistics;

/**
 * Track all sorts of statistics like total hits, time authorization took, API calls, etc.
 * 
 * @author Florian Quirin
 *
 */
public final class Statistics extends BasicStatistics{

	private Statistics() {
	}

	public static String getInfo(){
		//API (which endpoint?) in general
		int api_hits = api_total_hits.get();		int api_hits_a = api_auth_hits.get();
		long auth_time = auth_total_time.get();
		//Teach (submit sentence)
		int teach_hits_a = api_teach_hits.get();
		long teach_time = teach_total_time.get();
		//Feedback
		int feedback_hits_a = api_feedback_hits.get();
		long feedback_time = feedback_total_time.get();
		//Knowledge DB
		int kdb_write_hits_a = kdb_write_total_hits.get();	//authenticated writes
		long kdb_write_time = kdb_write_total_time.get();	//total time spent writing
		int kdb_read_hits_a = kdb_read_total_hits.get();	//authenticated reads
		long kdb_read_time = kdb_read_total_time.get();		//total time spent reading
		String msg = 
				"Total hits: " + api_hits + "<br><br>" +
				"Internal calls: " + api_internal_hits.get() + "<br>" +
				"Authentications: " + api_hits_a + "<br>" +
				"Auth. Time: " + (double)auth_time /api_hits_a + "ms (" + auth_time + "ms)" + "<br><br>" +
				"Teachings (sentences): " + teach_hits_a + "<br>" +
				"Teach Time: " + (double)teach_time /teach_hits_a + "ms (" + teach_time + "ms)" + "<br><br>" +
				"Feedback: " + feedback_hits_a + "<br>" +
				"Feedback Time: " + (double)feedback_time /feedback_hits_a + "ms (" + feedback_time + "ms)" + "<br><br>" +
				"KDB Write Time: " + (double)kdb_write_time /kdb_write_hits_a + "ms (" + kdb_write_time + "ms)" + "<br>" +
				"KDB Read Time: " + (double)kdb_read_time /kdb_read_hits_a + "ms (" + kdb_read_time + "ms)" + "<br><br>"
				;
		//add basics
		msg += getBasicInfo();
		
		return msg;
	}
	
	//API in general - replace as needed for different endpoints
	private static final AtomicInteger api_total_hits = new AtomicInteger(0);
	private static final AtomicInteger api_auth_hits = new AtomicInteger(0);
	private static final AtomicInteger api_internal_hits = new AtomicInteger(0);
	private static final AtomicLong auth_total_time = new AtomicLong(0);
	public static void add_API_hit(){
		api_total_hits.incrementAndGet();		//API hit counter
	}
	public static void add_API_hit_authenticated(){
		api_auth_hits.incrementAndGet();		//API authenticated hit counter
	}
	public static void add_API_hit_internal(){
		api_internal_hits.incrementAndGet();	//API internal call counter
	}
	public static void save_Auth_total_time(long tic){
		long time = System.currentTimeMillis()-tic;
		auth_total_time.addAndGet(time);		//save total time needed to do Answer API call
	}
	
	//Feedback
	private static final AtomicInteger api_feedback_hits = new AtomicInteger(0);
	private static final AtomicLong feedback_total_time = new AtomicLong(0);
	public static void add_feedback_hit(){
		api_feedback_hits.incrementAndGet();	//hit counter
	}
	public static void save_feedback_total_time(long tic){
		long time = System.currentTimeMillis()-tic;
		feedback_total_time.addAndGet(time);	//save total time needed to do API call
	}
	
	//Teach
	private static final AtomicInteger api_teach_hits = new AtomicInteger(0);
	private static final AtomicLong teach_total_time = new AtomicLong(0);
	public static void add_teach_hit(){
		api_teach_hits.incrementAndGet();	//hit counter
	}
	public static void save_teach_total_time(long tic){
		long time = System.currentTimeMillis()-tic;
		teach_total_time.addAndGet(time);	//save total time needed to do API call
	}
	
	//Knowledge database calls - Feedback, Teachings, Geodata, etc. ... should all be in this database
	private static final AtomicInteger kdb_write_total_hits = new AtomicInteger(0);		//successful database read
	private static final AtomicInteger kdb_read_total_hits = new AtomicInteger(0);		//successful database write
	private static final AtomicLong kdb_read_total_time = new AtomicLong(0);
	private static final AtomicLong kdb_write_total_time = new AtomicLong(0);
	public static void add_KDB_write_hit(){
		kdb_write_total_hits.incrementAndGet();			//Database hit counter
	}
	public static void add_KDB_read_hit(){
		kdb_read_total_hits.incrementAndGet();			//Database hit counter
	}
	public static void save_KDB_write_total_time(long tic){
		long time = System.currentTimeMillis()-tic;
		kdb_write_total_time.addAndGet(time);		//save total time needed to do successful database request
	}
	public static void save_KDB_read_total_time(long tic){
		long time = System.currentTimeMillis()-tic;
		kdb_read_total_time.addAndGet(time);		//save total time needed to do successful database request
	}
}
