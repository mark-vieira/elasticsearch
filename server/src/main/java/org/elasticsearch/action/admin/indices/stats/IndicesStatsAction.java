/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.action.admin.indices.stats;

import org.elasticsearch.action.ActionType;

public class IndicesStatsAction extends ActionType<IndicesStatsResponse> {

    public static final IndicesStatsAction INSTANCE = new IndicesStatsAction();
    public static final String NAME = "indices:monitor/stats";

    private IndicesStatsAction() {
        super(NAME, IndicesStatsResponse::new);
    }
}
