package net.b07z.sepia.server.teach.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import net.b07z.sepia.server.core.data.Answer;
import net.b07z.sepia.server.core.data.Language;
import net.b07z.sepia.server.teach.database.Elasticsearch;
import net.b07z.sepia.server.teach.server.Config;

/**
 * Import answers stored in a file to Elasticsearch.
 */
public class AnswerImporter {

	private void run(File file, Language language, Elasticsearch es, boolean isMachineTranslated) throws IOException {
		List<String> lines = Files.readAllLines(file.toPath());
		for (String line : lines) {
			Answer answer = Answer.importAnswerString(line, language, isMachineTranslated);
			System.out.println(answer);
			es.writeDocument(Config.DB_ANSWERS, Answer.ANSWERS_TYPE, answer.toJsonString());		
			//System.out.println(answer.toJsonString());
		}
	}

	public static void main(String[] args) throws IOException {
		AnswerImporter prg = new AnswerImporter();
		Elasticsearch es = new Elasticsearch("http://localhost:20724");
		prg.run(new File("C:/Users/Florian/workspace/gigaaa-assist-API/src/main/resources/answers/answers_de.txt"), Language.DE, es, false);

		//es.setupMapping(Config.DB_ANSWERS);
		//es.refresh();;
		//prg.run(new File("../misc/assistant-files/answers/answers_de.txt"), Language.DE, es, false);
	}
	
}
