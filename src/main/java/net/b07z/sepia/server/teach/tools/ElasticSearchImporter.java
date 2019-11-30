package net.b07z.sepia.server.teach.tools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import net.b07z.sepia.server.core.data.Answer;
import net.b07z.sepia.server.core.data.Command;
import net.b07z.sepia.server.core.data.Language;
import net.b07z.sepia.server.core.data.SentenceBuilder;
import net.b07z.sepia.server.core.server.ConfigDefaults;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.teach.database.Elasticsearch;
import net.b07z.sepia.server.teach.server.Config;

/**
 * Takes our CSV-like files and imports them into ElasticSearch.
 * 
 * NOTE: this is optimized for chats_xy files. teachIt_xy files might have many more parameters!
 */
class ElasticSearchImporter {

	private final Elasticsearch es;
	
	private String source;
	private Language language;
	private int docCount;
	private int answerCount;
	private int existingAnswerCount;

	ElasticSearchImporter(Elasticsearch es) {
		this.es = es;
	}
	
	void setSource(String source) {
		this.source = source;
	}

	void setLanguage(Language language) {
		this.language = language;
	}

	private void run(File file) throws IOException {
		System.out.println("Reading " + file);
		File idFile = File.createTempFile(ElasticSearchImporter.class.getSimpleName() + "_ids", ".log");
		try (FileWriter fw = new FileWriter(idFile)) {
			fw.write("# " + new Date() + "\n");
			List<String> lines = Files.readAllLines(file.toPath());
			for (String line : lines) {
				if (line.trim().isEmpty()) {
					continue;
				}
				try {
					String docId = writeToES(line);
					fw.write(docId + "\n");
				} catch (Exception e) {
					throw new RuntimeException("Fail on line: '" + line + "' in file " + file, e);
				}
			}
		}
		System.out.println("newly inserted answers: " + answerCount);
		System.out.println("existing answers: " + existingAnswerCount);
		System.out.println("docCount: " + docCount);
		System.out.println("idFile: " + idFile.getAbsolutePath());
	}
	
	private String writeToES(String line) {
		String json = makeStringForEs_v1(line);
		
		String id = es.writeDocument(Config.DB_COMMANDS, Command.COMMANDS_TYPE, JSON.parseStringOrFail(json));
		System.out.println("=> " + json);
		docCount++;
		return id;
	}
	public String makeStringForEs_v1(String line){
		String[] parts = line.split(";;\t*");
		String sentence = parts[0];
		String command = get("command", parts);
		String type = get("type", parts);
		String info = get("info", parts);
		String reply = get("reply", parts);
		//String characters = get("char", parts);
		int sourceIndex = line.indexOf("#source:");
		if (sourceIndex != -1) {
			String importedFrom = line.substring(sourceIndex + "#source:".length());
			if (importedFrom != null && !importedFrom.trim().isEmpty()){
				source = importedFrom.trim();
			}
		}
		SentenceBuilder sentenceBuilder;
		sentenceBuilder = new SentenceBuilder(sentence, ConfigDefaults.defaultAssistantUserId, source).setPublic(true);
		sentenceBuilder.setReplies(getReplyIds(reply));
		//TODO: add proper translatedFrom support, its in conflict with other comments like #source
		int translationIndex = line.indexOf("#translatedFrom:");
		if (translationIndex != -1) {
			String translatedFrom = line.substring(sourceIndex + "#translatedFrom:".length());
			if (translatedFrom != null && !translatedFrom.trim().isEmpty()){
				sentenceBuilder.setTranslatedFrom(translatedFrom.trim());
			}
		}
		sentenceBuilder.setPublic(true);
		sentenceBuilder.setLanguage(language);
		String cmdSummary = command;
		if (type != null) {
			cmdSummary += ";; type=" + type;
		}
		if (info != null) {
			cmdSummary += ";; info=" + info;
		}
		if (reply != null) {
			cmdSummary += ";; reply=" + reply;
		}
		// TODO: we should get rid of this and return data as JSON instead of such a string
		sentenceBuilder.setCmdSummary(cmdSummary);   // e.g. "chat;;	type=question;;	info=no_eat" 
		List<Command.Sentence> sentences = new ArrayList<>();
		sentences.add(sentenceBuilder.build());

		Command cmd = new Command(command);
		if (type != null) cmd.addParameter("type", type);
		if (info != null) cmd.addParameter("info", info);
		cmd.add(sentences);
		
		return cmd.toJsonString();
	}
	public String makeStringForES_v2(String line) {
		String[] parts = line.split(";;\t*");
		String sentence = parts[0];
		String command = parts[1].split("=", 2)[1];
		String[] rest = Arrays.copyOfRange(parts, 2, parts.length);
		HashMap<String, String> pvPairs = getPVs(rest);
		
		int sourceIndex = line.indexOf("#source:");
		if (sourceIndex != -1) {
			String importedFrom = line.substring(sourceIndex + "#source:".length());
			if (importedFrom != null && !importedFrom.trim().isEmpty()){
				source = importedFrom.trim();
			}
		}
		SentenceBuilder sentenceBuilder;
		sentenceBuilder = new SentenceBuilder(sentence, ConfigDefaults.defaultAssistantUserId, source).setPublic(true);
		sentenceBuilder.setReplies(getReplyIds(pvPairs.get("reply")));
		//TODO: add proper translatedFrom support, its in conflict with other comments like #source
		int translationIndex = line.indexOf("#translatedFrom:");
		if (translationIndex != -1) {
			String translatedFrom = line.substring(sourceIndex + "#translatedFrom:".length());
			if (translatedFrom != null && !translatedFrom.trim().isEmpty()){
				sentenceBuilder.setTranslatedFrom(translatedFrom.trim());
			}
		}
		sentenceBuilder.setPublic(true);
		sentenceBuilder.setLanguage(language);
		
		String cmdSummary = command;
		Command cmd = new Command(command);
		for (Entry<String, String> pv : pvPairs.entrySet()){
			cmdSummary += ";; "+ pv.getKey() + "=" + pv.getValue();
			cmd.addParameter(pv.getKey(), pv.getValue());
		}
		// TODO: we should get rid of this and return data as JSON instead of such a string
		sentenceBuilder.setCmdSummary(cmdSummary);   // e.g. "chat;;	type=question;;	info=no_eat"

		List<Command.Sentence> sentences = new ArrayList<>();
		sentences.add(sentenceBuilder.build());
		
		cmd.add(sentences);

		return cmd.toJsonString();
	}

	private List<String> getReplyIds(String reply) {
		if (reply != null) {
			String replyType = "individual";
			String[] replies = reply.split("\\|\\|");
			List<String> replyIds = new ArrayList<>();
			for (String r : replies) {
				String answerId = getExistingAnswerOrNull(r);
				if (answerId == null) {
					Answer answer = new Answer(language, replyType, r);
					String docId = es.writeDocument(Config.DB_ANSWERS, Answer.ANSWERS_TYPE, answer.toJsonString());
					replyIds.add(docId);
					answerCount++;
				} else {
					replyIds.add(answerId);
					existingAnswerCount++;
				}
			}
			return replyIds;
		}
		return Collections.emptyList();
	}

	private String getExistingAnswerOrNull(String text) {
		StringWriter sw = new StringWriter();
		try (JsonGenerator g = new JsonFactory().createGenerator(sw)) {
			g.writeStartObject();
				g.writeObjectFieldStart("query");
					g.writeObjectFieldStart("bool");
						g.writeArrayFieldStart("must");
						g.writeStartObject();
							g.writeObjectFieldStart("match");
								g.writeStringField("text", text);
							g.writeEndObject();
						g.writeEndObject();
					g.writeEndArray();
					g.writeEndObject();
				g.writeEndObject();
			g.writeEndObject();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		//System.out.println(sw.toString()); 		//debug
		JSONObject result = es.searchByJson(Config.DB_ANSWERS, sw.toString());
		JSONObject hitsObj = (JSONObject) result.get("hits");
		JSONArray hits = (JSONArray) hitsObj.get("hits");
		if (hits.size() > 0) {
			JSONObject firstHit = (JSONObject) hits.get(0);
			JSONObject firstHitSource = (JSONObject) firstHit.get("_source");
			//System.out.println("*** "+firstHitSource.get("text") + " ==? " + text + " --> " + firstHitSource.get("text").equals(text));
			if (firstHitSource.get("text").equals(text)) {
				return firstHit.get("_id").toString();
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	private String get(String name, String[] parts) {
		for (String part : parts) {
			if (part.trim().startsWith(name + "=")) {
				String r = part.replaceFirst(".*?=", "");
				if (r.contains("reply=") || r.contains("command=") || r.contains("type=") || r.contains("info=") || r.contains("char=")) {
					throw new RuntimeException("Key not removed: " + r);
				}
				return r.replaceFirst("#.*", "").trim();
			}
		}
		return null;
	}
	
	private HashMap<String, String> getPVs(String[] parts){
		HashMap<String, String> pvs = new HashMap<>();
		for (String part : parts) {
			if (part.contains("=")){
				String[] pv_temp = part.split("=", 2);
				pvs.put(pv_temp[0], pv_temp[1]);
			}
		}
		return pvs;
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length != 2) {
			throw new RuntimeException("Usage: " + ElasticSearchImporter.class.getSimpleName() + " <source> <languageCode>");
		}
		Elasticsearch es = new Elasticsearch("http://localhost:20724", null, null);
		//es.setupMapping(Config.DB_COMMANDS);
		//es.setupMapping(Config.DB_ANSWERS);
		ElasticSearchImporter prg = new ElasticSearchImporter(es);
		prg.setSource(args[0]);
		prg.setLanguage(Language.forValue(args[1]));
		
		args = new String[]{"import", "en"};
		prg.run(new File("../sepia-assist-API/Xtensions/Assistant/commands/chats_en.txt"));
	}

}
