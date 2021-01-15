/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */
package org.elasticsearch.action.admin.indices.template.get;

import org.elasticsearch.action.support.master.MasterNodeReadOperationRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;

public class GetIndexTemplatesRequestBuilder extends MasterNodeReadOperationRequestBuilder<
        GetIndexTemplatesRequest,
        GetIndexTemplatesResponse,
        GetIndexTemplatesRequestBuilder> {

    public GetIndexTemplatesRequestBuilder(ElasticsearchClient client, GetIndexTemplatesAction action) {
        super(client, action, new GetIndexTemplatesRequest());
    }

    public GetIndexTemplatesRequestBuilder(ElasticsearchClient client, GetIndexTemplatesAction action, String... names) {
        super(client, action, new GetIndexTemplatesRequest(names));
    }
}

