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

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.worker.JobWorker;
import io.zeebe.http.config.ConfigProvider;

import java.time.Duration;
import java.util.Optional;

public class ZeebeHttpWorker {

  private final String contactPoint;
  private ConfigProvider configProvider;

  private JobWorker jobWorker;

  public ZeebeHttpWorker(String contactPoint, ConfigProvider configProvider) {
    this.contactPoint = contactPoint;
    this.configProvider = configProvider;
  }

  public void start() {
    final ZeebeClient client =
        ZeebeClient.newClientBuilder()
            .brokerContactPoint(contactPoint)
            .defaultJobWorkerName("http-worker")
            .defaultJobTimeout(Duration.ofSeconds(10))
            .build();

    final HttpJobHandler jobHandler = new HttpJobHandler(configProvider);

    // fetch all variables to support expressions/placeholders
    jobWorker = client.newWorker().jobType("http").handler(jobHandler).open();
  }

  public void stop() {
    if (jobWorker != null) {
      jobWorker.close();
    }
  }
}
