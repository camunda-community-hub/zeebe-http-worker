/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.http;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.EnableZeebeClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
@EnableZeebeClient
public class ZeebeHttpWorkerApplication extends SpringBootServletInitializer {

  public static void main(String[] args) {
    SpringApplication.run(ZeebeHttpWorkerApplication.class, args);
    try {
      new CountDownLatch(1).await();
    } catch (InterruptedException e) {
    }
  }
  
  @Autowired
  private HttpJobHandler jobHandler;

  // This code does not limit the variables resolves
  // That means the worker always fetches all variables to support expressions/placeholders
  // as a workaround until https://github.com/zeebe-io/zeebe/issues/3417 is there
  @ZeebeWorker
  public void handleFooJob(final JobClient client, final ActivatedJob job) throws IOException, InterruptedException, ExecutionException, TimeoutException {
    jobHandler.handle(client, job);
  }  
}
