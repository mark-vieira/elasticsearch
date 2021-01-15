/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */
package org.elasticsearch.search.aggregations.bucket.nested;

import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.search.aggregations.bucket.ParsedSingleBucketAggregation;

import java.io.IOException;

public class ParsedNested extends ParsedSingleBucketAggregation implements Nested {

    @Override
    public String getType() {
        return NestedAggregationBuilder.NAME;
    }

    public static ParsedNested fromXContent(XContentParser parser, final String name) throws IOException {
        return parseXContent(parser, new ParsedNested(), name);
    }
}
