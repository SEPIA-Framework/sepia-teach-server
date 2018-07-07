package net.b07z.sepia.server.teach.database;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.json.simple.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

import net.b07z.sepia.server.core.tools.Connectors;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.teach.database.Elasticsearch;

@Ignore("Test need to be updated and are temporarily deactivated!") 		//TODO: update tests
public class ElasticsearchTest {
	
	public static final String UNIT_TEST_ES = "http://localhost:8080";

	private static final String index = "myindex";
	private static final String type = "mytype";
	private static final String id = "myid";

	@Test
	public void integrationTest() throws IOException {
		//noinspection unused
		Elasticsearch es = new Elasticsearch(UNIT_TEST_ES);
		writeDoc(es);
		testGetDocument(es);
		es.deleteDocument(index, type, id);
		doNotFindDocument(es);
		writeDoc(es);
		es.deleteAnything(index);
		doNotFindDocument(es);
		// TODO: test more
	}

	private void writeDoc(Elasticsearch es) {
		JSONObject json = new JSONObject();
		JSON.add(json, "myKey1", "my value");
		JSON.add(json, "myKey2", 11);
		es.writeDocument(index, type, id, json);
	}

	private void doNotFindDocument(Elasticsearch es) {
	    try {
	    	es.getDocument(index, type, id);
	    	fail("found doc that should be deleted");
	    } catch (RuntimeException e) {
	    	// expected
	    }
	}

	private void testGetDocument(Elasticsearch es) {
		JSONObject doc = es.getDocument(index, type, id);
		assertThat(Connectors.httpSuccess(doc), is(true));
		JSONObject source = (JSONObject) doc.get("_source");
		assertThat(source.get("myKey1"), is("my value"));
		assertThat(source.get("myKey2"), is(11L));
	}
	
}