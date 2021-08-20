# zeebe-http-worker

[![](https://img.shields.io/badge/Community%20Extension-An%20open%20source%20community%20maintained%20project-FF4700)](https://github.com/camunda-community-hub/community)

[![](https://img.shields.io/badge/Lifecycle-Stable-brightgreen)](https://github.com/Camunda-Community-Hub/community/blob/main/extension-lifecycle.md#stable-)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Zeebe worker to make HTTP calls (e.g. invoking a REST service). It is based on the built-in Java HttpClient.

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
  * `contentType` - the type of the request body (default: `application/json`, allowed: any valid HTTP content type)
  * `accept` - the type of the response body that is accepted (default: `application/json`, allowed: `application/json`, `text/plain`)
  * `statusCodeCompletion` - Status codes that lead to completion of the service task (default: `1xx,2xx`, allowed: comma separated list of codes including 1xx, 2xx, 3xx, 4xx and 5xx)
  * `statusCodeFailure` - Status codes that lead to the job failing  (default: `3xx,4xx,5xx`, allowed: comma separated list of codes including 1xx, 2xx, 3xx, 4xx and 5xx)
  * `errorCodePath` - path expression (dot notation) to extract the error code of a failed response body (e.g. `error.code`). If the error code is present then a BPMN error is thrown with this code instead of failing the job. Otherwise, that leads to the job failing.
  * `errorMessagePath` - path expression (dot notation) to extract the error message of a failed response body (e.g. `error.message`). If the error message is present then it is used as the error message of the BPMN error. Otherwise, a default error message is used.
  
* optional variables:
  * `body` - the request body as JSON
  * `authorization` - the value of the authorization header (e.g. `token 6bac4..`)
* jobs are completed with variables:
  * `statusCode` - the response status code
  * `body` - the response body, if present

### Placeholders

> Please note that the current way of handling placeholders is subject to change in the future, especially with https://github.com/zeebe-io/zeebe/issues/3417.

You can use placeholders in the form of `{{PLACEHOLDER}}` at all places, they will be replaced by 

* custom headers from the BPMN model
* Workflow variables
* Local Environment Variables or Configuration Variables from URL (see below)

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

To support some legacy scenarios the worker **at the moment** still also understands placeholders in the form of `${PLACEHOLDER}`. This is subject to be removed in later releases.

### HTTP Response codes

As described you can set the `statusCodeCompletion` and `statusCodeFailure` header to control the behavior depending on the HTTP Status. If the status code is in neither of the lists Zeebe will just keep waiting in the service task, allowing for asynchronous callbacks.

A common example is 

* Service returns HTTP 202 (ACCEPTED)
* Zeebe keeps waiting in the Service Task
* Until the external service somehow returns success and invokes the Zeebe API to complete the Job at hand

To allow this, the `jobKey` can be passed to the external service.

## Install

### Docker

The docker image for the worker is published to [GitHub Packages](https://github.com/orgs/camunda-community-hub/packages/container/package/zeebe-http-worker).

```
docker pull ghcr.io/camunda-community-hub/zeebe-http-worker:1.0.0
```
* configure the connection to the Zeebe broker by setting `zeebe.client.broker.contactPoint` (default: `localhost:26500`) 

For a local setup, the repository contains a [docker-compose file](docker/docker-compose.yml). It starts a Zeebe broker and the worker. 

```
cd docker
docker-compose up
```

### Manual

1. Download the latest [worker JAR](https://github.com/camunda-community-hub/zeebe-http-worker/releases) _(zeebe-http-worker-%{VERSION}.jar
)_

1. Start the worker
    `java -jar zeebe-http-worker-{VERSION}.jar`

### Readiness probes

You can check health of the worker:

  http://localhost:8080/actuator/health

This uses the Spring Actuator, so other metrics are available as well

## Configuration of Zeebe Connection

The connection to the broker Zeebe can be changed by setting the environment variables 

* `ZEEBE_CLIENT_BROKER_CONTACTPOINT` (default: `127.0.0.1:26500`).
* `ZEEBE_CLIENT_SECURITY_PLAINTEXT` (default: true).
* `ZEEBE_WORKER_DEFAULTNAME` (default: `http-worker`)
* `ZEEBE_WORKER_DEFAULTTYPE` (default: `http`)

This worker uses [Spring Zeebe]( https://github.com/zeebe-io/spring-zeebe/) underneath, so all configuration options available there are also available here.

## Configuration Variables

You can load additional configuration values used to substitute placeholders:
- Remotely via HTTP (`ENV_VARS_URL`)
- Locally using environment variables (if `ENV_VARS_URL` isn't set)

### Remote Variables Configuration

If `ENV_VARS_URL` is configured, the worker will query an HTTP endpoint and expects a JSON back:

```
[
  {
    "key": "someValue",
    "value": 42
  },
  {
    "key": "anotherValue",
    "value": 42
  }
]
```

To load additional config variables from an URL set these environment variables:

* `ENV_VARS_URL` (e.g. `http://someUrl/config`, default: null)
* `ENV_VARS_RELOAD_RATE` (default `15000`)
* `ENV_VARS_M2M_BASE_URL`
* `ENV_VARS_M2M_CLIENT_ID`
* `ENV_VARS_M2M_CLIENT_SECRET`
* `ENV_VARS_M2M_AUDIENCE`

### Local Environment Variables

To avoid exposing sensitive information, a prefix can be used to filter environment variables.

To change the prefix set these environment variables:
* `LOCAL_ENV_VARS_PREFIX` (default: `"ZEEBE_ENV_"`)
* `LOCAL_ENV_VARS_REMOVE_PREFIX` (default: `true`)

## Build from Source

Build with Maven

`mvn clean install`

## Code of Conduct

This project adheres to the Contributor Covenant [Code of
Conduct](/CODE_OF_CONDUCT.md). By participating, you are expected to uphold
this code. Please report unacceptable behavior to code-of-conduct@zeebe.io.
