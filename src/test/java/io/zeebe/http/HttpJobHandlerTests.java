package io.zeebe.http;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class HttpJobHandlerTests {

	@Test
	public void shouldResolveSingleTokenizedQueryParameterFromVariables(){

		HttpJobHandler sut = new HttpJobHandler();

		String url = "http://localhost:7200/api/posts?postid=$postid";
		Map<String, Object> variables = new HashMap<>();
		variables.put("postid", 1);

		url = sut.resolveQueryParameters(url, variables);

		Assert.assertEquals("http://localhost:7200/api/posts?postid=1", url);
	}

	@Test
	public void shouldResolveeMultipleTokenizedQueryParametersFromVariables(){

		HttpJobHandler sut = new HttpJobHandler();

		String url = "http://localhost:7200/api/posts?postid=$postid&commentid=$commentid&userid=$userid";
		Map<String, Object> variables = new HashMap<>();
		variables.put("postid", 1);
		variables.put("commentid", "c1");
		variables.put("userid", "john.doe");

		url = sut.resolveQueryParameters(url, variables);

		Assert.assertEquals("http://localhost:7200/api/posts?postid=1&commentid=c1&userid=john.doe", url);
	}

	@Test
	public void shouldNotAttemptToResolveeNonTokenizedQueryParameters(){

		HttpJobHandler sut = new HttpJobHandler();

		String url = "http://localhost:7200/api/posts?postid=$postid&userid=john.doe";
		Map<String, Object> variables = new HashMap<>();
		variables.put("postid", 1);
		variables.put("userid", "john.doe");

		url = sut.resolveQueryParameters(url, variables);

		Assert.assertEquals("http://localhost:7200/api/posts?postid=1&userid=john.doe", url);
	}
}
