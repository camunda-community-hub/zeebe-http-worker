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

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeebeHttpWorkerApplication {

  public static final String ENV_CONTACT_POINT = "zeebe.client.broker.contactPoint";
  private static final String DEFAULT_CONTACT_POINT = "127.0.0.1:26500";

  private static Logger LOG = LoggerFactory.getLogger("zeebe-http-worker");

  public static void main(String[] args) {

    final String contactPoint =
        Optional.ofNullable(System.getenv(ENV_CONTACT_POINT)).orElse(DEFAULT_CONTACT_POINT);

    LOG.info("Connecting worker to {}", contactPoint);

    final ZeebeHttpWorker worker = new ZeebeHttpWorker(contactPoint);
    worker.start();

    try {
      new CountDownLatch(1).await();
    } catch (InterruptedException e) {
    }
  }
}
