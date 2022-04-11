/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.rest.yaml.engine.descriptor;

import org.elasticsearch.test.rest.yaml.engine.YamlRestTestExecutionContext;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.hierarchical.Node;

abstract class BaseYamlTestDescriptor extends AbstractTestDescriptor implements Node<YamlRestTestExecutionContext> {
    protected BaseYamlTestDescriptor(UniqueId uniqueId, String displayName) {
        super(uniqueId, displayName);
    }

    protected BaseYamlTestDescriptor(UniqueId uniqueId, String displayName, TestSource source) {
        super(uniqueId, displayName, source);
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.SAME_THREAD;
    }
}
