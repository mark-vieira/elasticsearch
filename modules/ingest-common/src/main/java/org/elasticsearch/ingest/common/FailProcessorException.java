/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.ingest.common;

import org.elasticsearch.ingest.CompoundProcessor;
import org.elasticsearch.ingest.IngestDocument;

/**
 * Exception class thrown by {@link FailProcessor}.
 *
 * This exception is caught in the {@link CompoundProcessor} and
 * then changes the state of {@link IngestDocument}. This
 * exception should get serialized.
 */
public class FailProcessorException extends RuntimeException {

    public FailProcessorException(String message) {
        super(message);
    }
}

