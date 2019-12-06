package net.b07z.sepia.server.teach.database;

import net.b07z.sepia.server.core.tools.Debugger;
import net.b07z.sepia.server.core.tools.FilesAndStreams;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.server.teach.server.Config;

/**
 * Class to load data for Teach-UI.
 * 
 * @author Florian Quirin
 */
public class TeachUiDataLoader {
	
	private static String services;
	
	/**
	 * Return JSON string that contains all services shown inside the Teach-UI. 
	 * @throws Exception
	 */
	public static String getServices(Account account) throws Exception {
		if (services == null){
			Debugger.println("Loading Teach-UI services ...", 3);
			String s = FilesAndStreams.readFileModifyAndCache(Config.xtensionsFolder + "TeachUi/services/common.json", false, l -> {
				return (l.trim() + " ");
			});
			Debugger.println("Teach-UI services stored in cache.", 3);
			services = s;
			return services;
		}else{
			return services;
		}
	}

}
