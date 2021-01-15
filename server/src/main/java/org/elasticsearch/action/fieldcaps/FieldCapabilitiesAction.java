/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.action.fieldcaps;

import org.elasticsearch.action.ActionType;

public class FieldCapabilitiesAction extends ActionType<FieldCapabilitiesResponse> {

    public static final FieldCapabilitiesAction INSTANCE = new FieldCapabilitiesAction();
    public static final String NAME = "indices:data/read/field_caps";

    private FieldCapabilitiesAction() {
        super(NAME, FieldCapabilitiesResponse::new);
    }

}
