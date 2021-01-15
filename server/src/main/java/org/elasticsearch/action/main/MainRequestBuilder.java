/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.action.main;

import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;

public class MainRequestBuilder extends ActionRequestBuilder<MainRequest, MainResponse> {

    public MainRequestBuilder(ElasticsearchClient client, MainAction action) {
        super(client, action, new MainRequest());
    }
}
