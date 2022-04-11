/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.rest.yaml.engine.descriptor;

import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;

public class YamlDirectoryDescriptor extends BaseYamlTestDescriptor {

    public static final String SEGMENT_TYPE = "folder";

    public YamlDirectoryDescriptor(UniqueId uniqueId, String displayName) {
        super(uniqueId, displayName);
    }

    public YamlDirectoryDescriptor(UniqueId uniqueId, String displayName, TestSource source) {
        super(uniqueId, displayName, source);
    }

    @Override
    public Type getType() {
        return Type.CONTAINER;
    }
}
