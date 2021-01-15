/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.common.logging;

import org.elasticsearch.common.Strings;
import org.elasticsearch.common.SuppressLoggerChecks;

/**
 * A logger message used by {@link DeprecationLogger}.
 * Carries x-opaque-id field if provided in the headers. Will populate the x-opaque-id field in JSON logs.
 */
public class DeprecatedMessage  {
    public static final String X_OPAQUE_ID_FIELD_NAME = "x-opaque-id";
    public static final String ECS_VERSION = "1.6";

    @SuppressLoggerChecks(reason = "safely delegates to logger")
    public static ESLogMessage of(String key, String xOpaqueId, String messagePattern, Object... args) {
        ESLogMessage esLogMessage = new ESLogMessage(messagePattern, args)
            .field("data_stream.type", "logs")
            .field("data_stream.dataset", "deprecation.elasticsearch")
            .field("data_stream.namespace", "default")
            .field("ecs.version", ECS_VERSION)
            .field("key", key);

        if (Strings.isNullOrEmpty(xOpaqueId)) {
            return esLogMessage;
        }

        return esLogMessage.field(X_OPAQUE_ID_FIELD_NAME, xOpaqueId);
    }
}
