/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.common;

import java.security.SecureRandom;

class SecureRandomHolder {
    // class loading is atomic - this is a lazy & safe singleton to be used by this package
    public static final SecureRandom INSTANCE = new SecureRandom();
}
