/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.action;

public class ValidateActions {

    public static ActionRequestValidationException addValidationError(String error, ActionRequestValidationException validationException) {
        if (validationException == null) {
            validationException = new ActionRequestValidationException();
        }
        validationException.addValidationError(error);
        return validationException;
    }
}
