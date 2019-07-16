# zeebe-http-worker

A Zeebe worker to make HTTP calls (e.g. invoking a REST service). It is based on the built-in Java HttpClient.

> Requirements: Java 11

## Usage

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
  * `method` - the HTTP method to use (default: `GET`)
* optional variables:
  * `body` - the request body as JSON
  * `authorization` - the value of the authorization header (e.g. `token 6bac4..`)
* jobs are completed with variables:
  * `statusCode` - the response status code
  * `body` - the response body, if present


## Install

1) Download the [JAR file](https://github.com/zeebe-io/zeebe-http-worker/releases) 

2) Execute the JAR via

    `java -jar target/zeebe-http-worker-{VERSION}.jar`

### Configuration

The connection can be changed by setting the environment variables:
* `zeebe.client.broker.contactPoint` (default: `127.0.0.1:26500`).

## Build from Source

Build with Maven:
    
`mvn clean install`

## Code of Conduct

This project adheres to the Contributor Covenant [Code of
Conduct](/CODE_OF_CONDUCT.md). By participating, you are expected to uphold
this code. Please report unacceptable behavior to code-of-conduct@zeebe.io.
