/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.plugins;

import org.elasticsearch.Version;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.xcontent.ParsedMediaType;


/**
 * An extension point for Compatible API plugin implementation.
 */
public interface RestCompatibilityPlugin {
    /**
     * Returns a version which was requested on Accept and Content-Type headers
     *
     * @param acceptHeader      - a ParsedMediaType parsed from Accept header
     * @param contentTypeHeader - a ParsedMediaType parsed from Content-Type header
     * @param hasContent        - a flag indicating if a request has content
     * @return a requested Compatible API Version
     */
    Version getCompatibleVersion(@Nullable ParsedMediaType acceptHeader, @Nullable ParsedMediaType contentTypeHeader, boolean hasContent);
}
