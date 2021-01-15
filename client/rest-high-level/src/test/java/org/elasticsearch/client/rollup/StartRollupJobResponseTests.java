/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */
package org.elasticsearch.client.rollup;

import org.elasticsearch.client.core.AcknowledgedResponseTests;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;

import static org.elasticsearch.test.AbstractXContentTestCase.xContentTester;

public class StartRollupJobResponseTests extends ESTestCase {

    public void testFromXContent() throws IOException {
        xContentTester(this::createParser,
            this::createTestInstance,
            AcknowledgedResponseTests::toXContent,
            StartRollupJobResponse::fromXContent)
            .supportsUnknownFields(false)
            .test();
    }
    private StartRollupJobResponse createTestInstance() {
        return new StartRollupJobResponse(randomBoolean());
    }

}
