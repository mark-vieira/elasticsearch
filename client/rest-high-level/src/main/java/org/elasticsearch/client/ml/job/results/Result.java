/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */
package org.elasticsearch.client.ml.job.results;

import org.elasticsearch.common.ParseField;

/**
 * Contains common attributes for results.
 */
public final class Result {

    /**
     * Serialisation fields
     */
    public static final ParseField RESULT_TYPE = new ParseField("result_type");
    public static final ParseField TIMESTAMP = new ParseField("timestamp");
    public static final ParseField IS_INTERIM = new ParseField("is_interim");

    private Result() {
    }
}
