package net.b07z.sepia.server.teach.database;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.data.Language;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.server.teach.server.Config;

public final class ElasticsearchLogger implements DatabaseLogger {

	public static final String LOGS_TYPE = "all";
	
	private final TeachDatabase db;

	public ElasticsearchLogger(TeachDatabase db) {
		this.db = Objects.requireNonNull(db);
	}

	@Override
	public void log(Account account, String message, Language language) {
		JSONObject json = makeJson(account, message, language);
		db.setAnyItemData(Config.DB_LOGS, LOGS_TYPE, json);
	}

	@Override
	public void log(Account account, String message, Language language, String oldValue, String newValue) {
		JSONObject json = makeJson(account, message, language);
		JSON.add(json, "oldValue", oldValue);
		JSON.add(json, "newValue", newValue);
		db.setAnyItemData(Config.DB_LOGS, LOGS_TYPE, json);
	}

	private JSONObject makeJson(Account account, String message, Language language) {
		JSONObject json = new JSONObject();
		JSON.add(json, "date", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
		JSON.add(json, "user", account.getUserID());
		JSON.add(json, "message", Objects.requireNonNull(message));
		if (language != null) {
			JSON.add(json, "language", language.name().toLowerCase());
		}
		return json;
	}

}
