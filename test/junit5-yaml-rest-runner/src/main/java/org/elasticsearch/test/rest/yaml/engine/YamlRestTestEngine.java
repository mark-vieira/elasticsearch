/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.rest.yaml.engine;

import org.elasticsearch.test.rest.yaml.ClientYamlTestClient;
import org.elasticsearch.test.rest.yaml.engine.descriptor.YamlEngineDescriptor;
import org.elasticsearch.test.rest.yaml.engine.discovery.DiscoverySelectorResolver;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutorService;

public class YamlRestTestEngine extends HierarchicalTestEngine<YamlRestTestExecutionContext> {

    @Override
    public String getId() {
        return YamlEngineDescriptor.ENGINE_ID;
    }

    @Override
    protected YamlRestTestExecutionContext createExecutionContext(ExecutionRequest request) {
        ClientYamlTestClientFactory clientFactory = new ClientYamlTestClientFactory();
        ClientYamlTestClient clientYamlTestClient = clientFactory.buildClient();

        return new YamlRestTestExecutionContext(clientYamlTestClient);
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
        YamlEngineDescriptor engineDescriptor = new YamlEngineDescriptor(uniqueId);
        new DiscoverySelectorResolver(discoveryRequest).resolve(engineDescriptor);
        return engineDescriptor;
    }
}
