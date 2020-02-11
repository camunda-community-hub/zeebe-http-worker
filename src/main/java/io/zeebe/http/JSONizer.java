package io.zeebe.http;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.Map;

public class JSONizer {
    private ScriptEngine ifYouWantJSONYouGotIt;

    public JSONizer () {
        this.ifYouWantJSONYouGotIt = new ScriptEngineManager().getEngineByName("js");
        try {
            // Object.assign polyfill
            this.ifYouWantJSONYouGotIt.eval("if (typeof Object.assign !== 'function') {\n" +
                    "  // Must be writable: true, enumerable: false, configurable: true\n" +
                    "  Object.defineProperty(Object, \"assign\", {\n" +
                    "    value: function assign(target, varArgs) { // .length of function is 2\n" +
                    "      'use strict';\n" +
                    "      if (target === null || target === undefined) {\n" +
                    "        throw new TypeError('Cannot convert undefined or null to object');\n" +
                    "      }\n" +
                    "\n" +
                    "      var to = Object(target);\n" +
                    "\n" +
                    "      for (var index = 1; index < arguments.length; index++) {\n" +
                    "        var nextSource = arguments[index];\n" +
                    "\n" +
                    "        if (nextSource !== null && nextSource !== undefined) { \n" +
                    "          for (var nextKey in nextSource) {\n" +
                    "            // Avoid bugs when hasOwnProperty is shadowed\n" +
                    "            if (Object.prototype.hasOwnProperty.call(nextSource, nextKey)) {\n" +
                    "              to[nextKey] = nextSource[nextKey];\n" +
                    "            }\n" +
                    "          }\n" +
                    "        }\n" +
                    "      }\n" +
                    "      return to;\n" +
                    "    },\n" +
                    "    writable: true,\n" +
                    "    configurable: true\n" +
                    "  });\n" +
                    "}");
        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }

    public String deepMerge(Map<String, String> customHeaders, Map<String, Object> variables) {
        String script = "function JSONize(val) {" +
            "if (typeof val !== 'string') { return val }" +
                " else { " +
                "   try {" +
                "      return JSON.parse(val)" +
                "   } catch (e) {" +
                "      return val " +
                "   }" +
                "}" +
                "function deepJSON(obj) { " +
                "  var deeplyJSON = {}" +
                "  Object.keys(obj).forEach(function (key) {" +
                "    deeplyJSON[key] = JSONize(obj[key])" +
                "  })" +
                "}" +
                "var body = JSON.stringify(Object.assign({}, deepJSON(customHeaders), deepJSON(variables)).body || {})";

        this.ifYouWantJSONYouGotIt.put("customHeaders", customHeaders);
        this.ifYouWantJSONYouGotIt.put("variables", variables);
        try {
            Bindings bodyJSON = (Bindings) this.ifYouWantJSONYouGotIt.eval(script);
            return (String) bodyJSON.get("body");
        } catch (ScriptException e) {
            e.printStackTrace();
            return "";
        }
    }
}
