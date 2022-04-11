/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.util;

public abstract class ConfigUtils {
    public static boolean getBooleanProperty(String property, boolean defaultValue) {
        String propertyValue = System.getProperty(property);
        if (propertyValue == null) {
            return defaultValue;
        }
        if ("true".equals(propertyValue)) {
            return true;
        } else if ("false".equals(propertyValue)) {
            return false;
        } else {
            throw new RuntimeException("Sysprop [" + property + "] must be [true] or [false] but was [" + propertyValue + "]");
        }
    }
}
