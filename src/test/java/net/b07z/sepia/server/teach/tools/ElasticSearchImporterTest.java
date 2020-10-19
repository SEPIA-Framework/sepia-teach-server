package net.b07z.sepia.server.teach.tools;

import org.junit.Ignore;
import org.junit.Test;

import net.b07z.sepia.server.core.data.Language;
import net.b07z.sepia.server.teach.database.Elasticsearch;
import net.b07z.sepia.server.teach.database.ElasticsearchTest;
import net.b07z.sepia.server.teach.tools.ElasticSearchImporter;

import static org.hamcrest.core.Is.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

@Ignore("Test need to be updated and are temporarily deactivated!") 		//TODO: update tests
public class ElasticSearchImporterTest {
	
	@Test
	public void testMakeStringForES() throws IOException{
		Elasticsearch es = new Elasticsearch(ElasticsearchTest.UNIT_TEST_ES,
				ElasticsearchTest.UNIT_TEST_ES_AUTH_TYPE, ElasticsearchTest.UNIT_TEST_ES_AUTH_DATA);
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
