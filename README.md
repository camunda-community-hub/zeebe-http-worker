# zeebe-http-worker

A Zeebe worker to make HTTP calls (e.g. invoking a REST service). It is based on the built-in Java HttpClient.

> Requirements: Java 11

The worker subscribes to service tasks of type `CAMUNDA-HTTP`.

For POST, PUT and PATCH requests, the worker will invoke the outbound REST service with a JSON payload containing all process instance variables by default. The data to be passed on can be configured via input mapping on the BPMN service task. For GET and DELETE requests, no process variables can be passed on to the outbound service yet.

The data being returned by the called service (which is expected to be JSON) will be passed back to Zeebe as process instance variables. Again, this can be configuring via output mapping on the BPMN service task.

The job in Zeebe will be marked as completed as long as the called webservice responds with the HTTP status code 200. Otherwise the job will be marked as failed.

## Usage

### BPMN Task

Example BPMN with service task:

```xml
<bpmn:serviceTask id="http-get" name="stargazers check">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="http" />
    <zeebe:taskHeaders>
      <zeebe:header key="url" value="https://api.github.com/user/starred/zeebe-io/zeebe-http-worker" />
    </zeebe:taskHeaders>
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

* the worker is registered for the type `http`
* required custom headers/variable:
  * `url` - the url to invoke
* optional custom headers:
  * `method` - the HTTP method to use (default: `GET`, allowed:  `post` | `get` | `put` | `delete` | `patch`)
  * `statusCodeCompletion` - Status codes that lead to completion of the service task (default: `1xx,2xx`, allowed: comma separated list of codes including 1xx, 2xx, 3xx, 4xx and 5xx)
  * `statusCodeFailure` - Status codes that lead to the job failing  (default: `3xx,4xx,5xx`, allowed: comma separated list of codes including 1xx, 2xx, 3xx, 4xx and 5xx)
  
* optional variables:
  * `body` - the request body as JSON
  * `authorization` - the value of the authorization header (e.g. `token 6bac4..`)
* jobs are completed with variables:
  * `statusCode` - the response status code
  * `body` - the response body, if present

### Placeholders

You can use placeholders in the form of `{{PLACEHOLDER}}` at all places, they will be replaced by 

* custom headers from the BPMN model
* Workflow variables
* Configuration Variables from URL (see below)

[Mustache](https://github.com/spullara/mustache.java) is used for replacing the placeholders, refer to their docs to check possibilities.

Example:

```xml
<bpmn:serviceTask id="http-get" name="stargazers check">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="http" />
    <zeebe:taskHeaders>
      <zeebe:header key="url" value="https://{{BASE_URL}}/order?id={{orderId}}" />
      <zeebe:header key="method" value="GET" />
    </zeebe:taskHeaders>
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

`BASE_URL` could be configured by the configuration variables from the URL and the `orderId` might be a workflow variable.

### HTTP Response codes

As described you can set the `statusCodeCompletion` and `statusCodeFailure` header to control the behavior depending on the HTTP Status. If the status code is in neither of the lists Zeebe will just keep waiting in the service task, allowing for asynchronous callbacks.

A common example is 

* Service returns HTTP 202 (ACCEPTED)
* Zeebe keeps waiting in the Service Task
* Until the external service somehow returns success and invokes the Zeebe API to complete the Job at hand

To allow this, the `jobKey` can be passed to the external service.


## Install

### JAR 

* Download the [JAR file](https://github.com/zeebe-io/zeebe-http-worker/releases) 
* Execute the JAR via

    `java -jar target/zeebe-http-worker-{VERSION}.jar`

### Docker

    `docker run camunda/zeebe-http-worker`

### Build from Source

Build with Maven:
    
`mvn clean install`

## Configuration of Zeebe Connection

The connection to the broker Zeebe can be changed by setting the environment variables 

* `zeebe.client.broker.contactPoint` (default: `127.0.0.1:26500`).
* `zeebe.client.security.plaintext` (default: true).
* `zeebe.worker.name` (default `http-worker`)

This worker uses [Spring Zeebe]( https://github.com/zeebe-io/spring-zeebe/) underneath, so all configuration options available there are also available here.

## Configuration Variables from URL

You can load additional configuration values used to substitute placeholders. Therefor the worker will query an HTTP endpoint and expects a JSON back:

```
{
  "someValue": 42",
  "anotherValue": "42"
}
```

To load additional config variables from an URL set these environment variables:

* `ENV_VARS_URL` (e.g. `http://someUrl/config`, default: null)
* `ENV_VARS_RELOAD_RATE` (defaullt `15000`)
* `ENV_VARS_M2M_BASE_URL`
* `ENV_VARS_M2M_CLIENT_ID`
* `ENV_VARS_M2M_CLIENT_SECRET`
* `ENV_VARS_M2M_AUDIENCE`


## Code of Conduct

This project adheres to the Contributor Covenant [Code of
Conduct](/CODE_OF_CONDUCT.md). By participating, you are expected to uphold
this code. Please report unacceptable behavior to code-of-conduct@zeebe.io.
