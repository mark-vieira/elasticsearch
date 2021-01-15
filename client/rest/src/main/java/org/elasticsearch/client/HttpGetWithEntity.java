/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */
package org.elasticsearch.client;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;

import java.net.URI;

/**
 * Allows to send GET requests providing a body (not supported out of the box)
 */
final class HttpGetWithEntity extends HttpEntityEnclosingRequestBase {

    static final String METHOD_NAME = HttpGet.METHOD_NAME;

    HttpGetWithEntity(final URI uri) {
        setURI(uri);
    }

    @Override
    public String getMethod() {
        return METHOD_NAME;
    }
}
