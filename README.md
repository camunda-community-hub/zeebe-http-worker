# Zeebe HTTP extension
This is a Zeebe worker to make HTTP calls (e.g. invoke a REST service).
The worker based on the [Zeebe Go client](https://github.com/zeebe-io/zbc-go).  

* the worker is registered for the type 'http'
* required parameter: 'url' as custom header or payload
* optional parameters: 'method' (e.g. "GET", "POST"), 'body' (as JSON)
* output payload contains 'statusCode' and 'body'  

_This is a community project meant for playing around with Zeebe. It is not officially supported by the Zeebe Team (i.e. no gurantees). Everybody is invited to contribute!_

## Example
HTTP GET request:
```
<bpmn:serviceTask id="httpCall" name="Get posts"
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="http" />
    <zeebe:taskHeaders>
      <zeebe:header key="url" value="https://jsonplaceholder.typicode.com/posts/" />
    </zeebe:taskHeaders>
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

Result is:
```
{
  "statusCode": 200,
  "body": [
    {
      "userId": 1,
      "id": 1,
      "title": "sunt aut facere repellat provident occaecati excepturi optio reprehenderit",
      "body": "quia et suscipit\nsuscipit recusandae consequuntur expedita et cum\nreprehenderit molestiae ut ut quas totam\nnostrum rerum est autem sunt rem eveniet architecto"
    },
    ...
}
```

HTTP POST request:
```
<bpmn:serviceTask id="httpCall" name="New post"
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="http" />
    <zeebe:taskHeaders>
      <zeebe:header key="url" value="https://jsonplaceholder.typicode.com/posts/" />
      <zeebe:header key="method" value="https://jsonplaceholder.typicode.com/posts/" />
    </zeebe:taskHeaders>
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

with Payload:
```
{
  "body": {
      "userId": 99,
      "title": "foo",
      "body": "bar"
    }
}
```

## Configuration
The worker can be configured via environment variables.

* BROKER = the connection string (default: "0.0.0.0:51015")
* TOPIC = the topic to subscribe to (default: "default-topic")  

## Code of Conduct

This project adheres to the Contributor Covenant [Code of
Conduct](/CODE_OF_CONDUCT.md). By participating, you are expected to uphold
this code. Please report unacceptable behavior to code-of-conduct@zeebe.io.
