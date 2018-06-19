package net.b07z.sepia.server.teach.tools;

import org.junit.Test;

import net.b07z.sepia.server.core.data.Language;
import net.b07z.sepia.server.teach.database.Elasticsearch;
import net.b07z.sepia.server.teach.database.ElasticsearchTest;
import net.b07z.sepia.server.teach.tools.ElasticSearchImporter;

import static org.junit.Assert.assertThat;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;

public class ElasticSearchImporterTest {
	
	@Test
	public void testMakeStringForES() throws IOException{
		Elasticsearch es = new Elasticsearch(ElasticsearchTest.UNIT_TEST_ES);
		String[] args = new String[]{"import", "en"};
		ElasticSearchImporter prg = new ElasticSearchImporter(es);
		prg.setSource(args[0]);
		prg.setLanguage(Language.forValue(args[1]));
		
		String line = "this is a test;; command=chat;; type=testQ;; reply=works;; someparam=something;; #source:junit";
		
		String a = prg.makeStringForEs_v1(line);
		String b = prg.makeStringForES_v2(line);
		System.out.println(a);
		System.out.println(b);
		assertThat(a, is(b));
	}

}
