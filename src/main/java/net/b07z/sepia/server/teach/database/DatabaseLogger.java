package net.b07z.sepia.server.teach.database;

import net.b07z.sepia.server.core.data.Language;
import net.b07z.sepia.server.core.users.Account;

/**
 * Logging write accesses by users (e.g. sentence changes) to some kind of database.
 */
public interface DatabaseLogger {

	void log(Account account, String message, Language language);

	void log(Account account, String message, Language language, String oldValue, String newValue);

}
