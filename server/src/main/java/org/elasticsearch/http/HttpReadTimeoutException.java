/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */
package org.elasticsearch.http;

public class HttpReadTimeoutException extends RuntimeException {

    public HttpReadTimeoutException(long readTimeoutMillis) {
        super("http read timeout after " + readTimeoutMillis + "ms");

    }

    public HttpReadTimeoutException(long readTimeoutMillis, Exception cause) {
        super("http read timeout after " + readTimeoutMillis + "ms", cause);
    }
}
