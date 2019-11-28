package io.zeebe.http;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Map.Entry;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

public class PlaceholderProcessor {

  private final MustacheFactory mf = new DefaultMustacheFactory();

  public String process(String input, Map<String, Object> context) {
    final StringWriter output = new StringWriter();

    final Mustache mustache = mf.compile(new StringReader(input), "");
    mustache.execute(output, context);

    String result = legacyProcess(output.toString(), context);
    return result;
  }

  /**
   * Also process the old way of having placeholders in the Cloud.
   * Can hopefully be removed when everything is touched with https://github.com/zeebe-io/zeebe/issues/3417.
   */
  public String legacyProcess(String input,  Map<String, Object> context) {
    if (input==null || context==null) {
      return null;
    }
    // RegEx: .*(\$\{hallo\}).* 
    for (Entry<String, Object> entry : context.entrySet()) {
      input = input.replaceAll("\\$\\{" + entry.getKey() + "\\}", String.valueOf(entry.getValue()));        
    }
    return input;
  }
}
