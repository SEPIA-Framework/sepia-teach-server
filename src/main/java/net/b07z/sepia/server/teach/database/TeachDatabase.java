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
import net.b07z.sepia.server.core.database.DatabaseInterface;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.server.teach.data.Vote;

/**
 * Interface for classes that supply database access.
 * 
 * @author Florian Quirin
 *
 */
public interface TeachDatabase extends DatabaseInterface{
	
	//---- common db methods ----

	/**
	 * A database may not be real-time. In those cases, this will force a refresh so that
	 * recent changes become visible in upcoming searches.<br>
	 * See https://www.elastic.co/guide/en/elasticsearch/guide/current/near-real-time.html
	 */
	void refresh();
	
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
	 * and take max. 100 results (and use index + type instead of path).
	 * @param index - Index to search
	 * @param type - Type in index to search
	 * @param query - something to search like "name:John" or simply "John" or "*" for all.
	 * @return
	 */
	JSONObject search(String index, String type, String query);
	

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
	 * <li>button - get only results that should show up as buttons
	 * <li>from - starting results from ... (optional, starts with 0)
	 * <li>size - size of results (optional, defaults to 10)
	 * </ul>
	 */
	JSONArray getAllPersonalCommands(HashMap<String, Object> filters);
	
	/**
	 * Get all custom sentences for a given language with some additional info (e.g. user intent) to be used as training data for ML. 
	 * @param language - ISO language code
	 * @return
	 */
	JSONArray getAllCustomSentencesAsTrainingData(String language);
	
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
