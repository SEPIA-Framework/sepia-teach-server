package net.b07z.sepia.server.teach.database;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.data.Answer;
import net.b07z.sepia.server.core.data.CmdMap;
import net.b07z.sepia.server.core.data.Command;
import net.b07z.sepia.server.core.data.Language;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.server.teach.data.Vote;

/**
 * Interface for classes that supply database access.
 * 
 * @author Florian Quirin
 *
 */
public interface TeachDatabase {
	
	//---- common db methods ----

	/**
	 * A database may not be real-time. In those cases, this will force a refresh so that
	 * recent changes become visible in upcoming searches.
	 */
	void refresh();
	
	/**
	 * Set the data/properties/values of an item of "type" at "index". 
	 * @param index - index or table name like e.g. "account" or "knowledge"
	 * @param type - subclass name, e.g. "user", "lists", "banking" (for account) or "geodata" and "dictionary" (for knowledge) 
	 * @param itemId - unique item/id name, e.g. user email address, dictionary word or geodata location name
	 * @param data - JSON string with data objects that should be stored for index/type/item, e.g. {"name":"john"}
	 */
	void setItemData(String index, String type, String itemId, JSONObject data);
	
	/**
	 * Set the data/properties/values of an arbitrary item of "type" at "index". Use this if you don't care about the unique id like
	 * when you store blog posts. The item id will be generated automatically.
	 * @param index - index or table name like e.g. "homepage"
	 * @param type - subclass name, e.g. "blogpost"
	 * @param data - JSON string with data objects that should be stored for index/type/any_id, e.g. {"title":"Hot News", "body":"bla bla...", "author":"john james"}
	 */
	void setAnyItemData(String index, String type, JSONObject data);
	
	/**
	 * Get item at path "index/type/item_id"
	 * @param index - index or table name like e.g. "account" or "knowledge"
	 * @param type - subclass name, e.g. "user", "lists", "banking" (for account) or "geodata" and "dictionary" (for knowledge) 
	 * @param itemId - unique item/id name, e.g. user email address, dictionary word or geodata location name
	 * @return JSONObject with result or error description
	 */
	JSONObject getItem(String index, String type, String itemId);
	
	/**
	 * Get filtered entries of the item at path "index/type/item_id". Use this if you want for example only a user name or something.
	 * @param index - index or table name like e.g. "account" or "knowledge"
	 * @param type - subclass name, e.g. "user", "lists", "banking" (for account) or "geodata" and "dictionary" (for knowledge) 
	 * @param itemId - unique item/id name, e.g. user email address, dictionary word or geodata location name
	 * @param filters - String array with filters like {"name", "address", "language", "age"}
	 * @return JSONObject with result or error description
	 */
	JSONObject getItemFiltered(String index, String type, String itemId, String[] filters);
	
	/**
	 * Search at "path" for a keyword.
	 * @param path - can be index, type or item, e.g. "index/type/item_id" or only "index/"
	 * @param searchTerm - something to search like "name:John" or simply "John" or "*" for all.
	 * @param from - start at result X
	 * @param size - take max. X results
	 * @return JSONObject with search result or error description
	 * @return
	 */
	JSONObject searchSimple(String path, String searchTerm, int from, int size);
	
	/**
	 * Search at index, type (path) for a keyword. Same as 'searchSimple' but always start at 0 
	 * and take max. 100 results.
	 * @param index - Index to search
	 * @param type - Type in index to search
	 * @param query - something to search like "name:John" or simply "John" or "*" for all.
	 * @return
	 */
	JSONObject search(String index, String type, String query);
	
	/**
	 * Post a JSON query to ES - to be used for internal tools where we need full query flexibility.
	 */
	JSONObject searchByJson(String path, String queryJson);
	
	/**
	 * Delete a entry by using a JSON query 
	 */
	JSONObject deleteByJson(String path, String jsonQuery);
		
	/**
	 * Delete item of "type" at "index".
	 * @param index - index or table name like e.g. "account"
	 * @param type - subclass name, e.g. "user"
	 * @param itemId - item to delete, e.g. a user_id (email)
	 */
	void deleteItem(String index, String type, String itemId);
	
	/**
	 * Delete any object like "index" (with all entries), "type" or "item"
	 * @param path - path of what you want to delete like "account/" or "account/banking/"
	 */
	void deleteAnything(String path);

	
	//-----teach API specific methods-----

	/**
	 * Get list of command-to-services mappings.
	 * @param userId - id of user
	 * @param customOrSystem - type of services CmdMap.CUSTOM or CmdMap.SYSTEM
	 * @param filters - additional filters tbd
	 * @return
	 */
	List<CmdMap> getCustomCommandMappings(String userId, String customOrSystem, HashMap<String, Object> filters);
	
	/**
	 * Add command-to-services mappings to user data. 
	 * @param userId - id of user
	 * @param customOrSystem - type of services CmdMap.CUSTOM or CmdMap.SYSTEM
	 * @param mappings - mappings to be added
	 * @para filters - additional filters like 'overwrite' (true/false) and more (tbd)
	 */
	void setCustomCommandMappings(String userId, String customOrSystem, Set<CmdMap> mappings, HashMap<String, Object> filters);
	
	/**
	 * Get a user's personal commands. {@code filters} can contain these keys:
	 * <ul>
	 * <li>userId - id of the user
	 * <li>language - load results of this language 
	 * <li>includePublic - include public results?
	 * <li>searchText - optional query
	 * </ul> 
	 */
	JSONArray getPersonalCommands(HashMap<String, Object> filters);
	
	/**
	 * Basically its the same as 'getPersonalCommands' but simplified to get all user's personal commands independent form search text.
	 * {@code filters} can contain these keys:
	 * <ul>
	 * <li>userId - id of the user
	 * <li>language - load results of this language (optional)
	 * <li>from - starting results from ... (optional, starts with 0)
	 * </ul>
	 */
	JSONArray getAllPersonalCommands(HashMap<String, Object> filters);
	
	/**
	 * Search a command defined by the userId, language and a text to match.
	 * @return ID or empty string
	 */
	String getIdOfCommand(String userId, String language, String textToMatch);
	
	/**
	 * Let user submit a (public or private) command that the assistant should know in the future.
	 */
	void submitCommand(Command cmd);
	/**
	 * Let user submit a (public or private) command with a fixed id (e.g. to overwrite old).
	 */
	void submitCommand(Command cmd, String id);
	/**
	 * Delete command defined by user.
	 */
	JSONObject deleteCommand(String id, String userId);

	/**
	 * Add an answer.
	 * @param answer
	 * @param userAccount
	 */
	void addAnswer(Answer answer, Account userAccount);
	
	/**
	 * Get answers by their 'type' (e.g. 'no_answer_0a').
	 */
	JSONObject getAnswersByType(String index, String type, String answerType, String languageOrNull, String userOrNull);
	
	/**
	 * Get any answer just by ID.
	 * @param docId
	 */
	JSONObject getAnswerById(String docId);
	
	/**
	 * Delete answer using document ID (and matching user ID).
	 * @param docId
	 * @param userId
	 */
	JSONObject deleteAnswerById(String docId, String userId);
	
	/**
	 * Modify answer text.
	 * @param id document id
	 */
	void modifyAnswer(String id, Language language, String oldText, String newText);

	/**
	 * Store a user's vote on an answer.
	 */
	void voteAnswer(String docId, String votedSentence, Language votedLanguage, Vote vote, Account account);

	/**
	 * Add a sentence, i.e. a variation that has the same meaning as the other sentences of this document.
	 * @param id document id
	 */
	void addSentence(String id, Language language, String text, Account userAccount);

	/**
	 * Store a user's vote on a sentence.
	 */
	void voteSentence(String docId, String votedSentence, Language votedLanguage, Vote vote, Account account);
	
	/**
	 * Get sentences that have been reported by feedback end-point. 
	 * @param filters - HashMap to submit arbitrary search filters
	 * @return JSONArray with all (filtered) reported feedback
	 */
	JSONArray getReportedFeedback(HashMap<String, Object> filters, int from, int size);
	/**
	 * Get sentences that have been liked by feedback end-point. 
	 * @param filters - HashMap to submit arbitrary search filters
	 * @return JSONArray with all (filtered) liked feedback
	 */
	JSONArray getLikedFeedback(HashMap<String, Object> filters, int from, int size);

	/**
	 * Get logs about write actions.
	 */
	JSONObject getLogs(String index, String type, int from, int size);

}
