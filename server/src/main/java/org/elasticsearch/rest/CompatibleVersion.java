/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.rest;

import org.elasticsearch.Version;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.xcontent.ParsedMediaType;

/**
 * An interface used to specify a function that returns a compatible API version.
 * This function abstracts how the version calculation is provided (for instance from plugin).
 */
@FunctionalInterface
public interface CompatibleVersion {
    Version get(@Nullable ParsedMediaType acceptHeader, @Nullable ParsedMediaType contentTypeHeader, boolean hasContent);

    CompatibleVersion CURRENT_VERSION = (acceptHeader, contentTypeHeader, hasContent) -> Version.CURRENT;
}
