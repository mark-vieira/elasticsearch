/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.gradle.testclusters;

import org.elasticsearch.gradle.Distribution;
import org.elasticsearch.gradle.FileSupplier;
import org.gradle.api.logging.Logging;
import org.slf4j.Logger;

import java.io.File;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;


public interface TestClusterConfiguration {

    void setVersion(String version);

    void setDistribution(Distribution distribution);

    void plugin(URI plugin);

    void plugin(File plugin);

    void module(File module);

    void keystore(String key, String value);

    void keystore(String key, Supplier<CharSequence> valueSupplier);

    void keystore(String key, File value);

    void keystore(String key, FileSupplier valueSupplier);

    void setting(String key, String value);

    void setting(String key, Supplier<CharSequence> valueSupplier);

    void systemProperty(String key, String value);

    void systemProperty(String key, Supplier<CharSequence> valueSupplier);

    void environment(String key, String value);

    void environment(String key, Supplier<CharSequence> valueSupplier);

    void freeze();

    void setJavaHome(File javaHome);

    void start();

    void extraConfigFile(String destination, File from);

    String getHttpSocketURI();

    String getTransportPortURI();

    List<String> getAllHttpSocketURI();

    List<String> getAllTransportPortURI();

    void stop(boolean tailLogs);

    default void waitForConditions(
        LinkedHashMap<String, Predicate<TestClusterConfiguration>> waitConditions,
        long startedAtMillis,
        long nodeUpTimeout, TimeUnit nodeUpTimeoutUnit,
        TestClusterConfiguration context
    ) {
        Logger logger = Logging.getLogger(TestClusterConfiguration.class);
        waitConditions.forEach((description, predicate) -> {
            long thisConditionStartedAt = System.currentTimeMillis();
            boolean conditionMet = false;
            Throwable lastException = null;
            while (
                System.currentTimeMillis() - startedAtMillis < TimeUnit.MILLISECONDS.convert(nodeUpTimeout, nodeUpTimeoutUnit)
            ) {
                if (context.isProcessAlive() == false) {
                    throw new TestClustersException(
                        "process was found dead while waiting for " + description + ", " + this
                    );
                }

                try {
                    if(predicate.test(context)) {
                        conditionMet = true;
                        break;
                    }
                } catch (TestClustersException e) {
                    throw new TestClustersException(e);
                } catch (Exception e) {
                    if (lastException == null) {
                        lastException = e;
                    } else {
                        lastException = e;
                    }
                }
                try {
                    Thread.sleep(500);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (conditionMet == false) {
                String message = "`" + context + "` failed to wait for " + description + " after " +
                    nodeUpTimeout + " " + nodeUpTimeoutUnit;
                if (lastException == null) {
                    throw new TestClustersException(message);
                } else {
                    throw new TestClustersException(message, lastException);
                }
            }
            logger.info(
                "{}: {} took {} seconds",
                this,  description,
                (System.currentTimeMillis() - thisConditionStartedAt) / 1000.0
            );
        });
    }

    default String safeName(String name) {
        return name
            .replaceAll("^[^a-zA-Z0-9]+", "")
            .replaceAll("[^a-zA-Z0-9]+", "-");
    }



    boolean isProcessAlive();
}
