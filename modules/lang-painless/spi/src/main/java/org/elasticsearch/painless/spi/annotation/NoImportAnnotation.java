/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.painless.spi.annotation;

public class NoImportAnnotation {

    public static final String NAME = "no_import";

    public static final NoImportAnnotation INSTANCE = new NoImportAnnotation();

    private NoImportAnnotation() {

    }
}
