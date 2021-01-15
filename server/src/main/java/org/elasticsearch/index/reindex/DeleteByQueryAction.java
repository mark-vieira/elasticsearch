/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.index.reindex;

import org.elasticsearch.action.ActionType;

public class DeleteByQueryAction extends ActionType<BulkByScrollResponse> {

    public static final DeleteByQueryAction INSTANCE = new DeleteByQueryAction();
    public static final String NAME = "indices:data/write/delete/byquery";

    private DeleteByQueryAction() {
        super(NAME, BulkByScrollResponse::new);
    }
}
