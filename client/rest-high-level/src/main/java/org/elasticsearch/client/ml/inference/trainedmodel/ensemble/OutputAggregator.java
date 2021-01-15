/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */
package org.elasticsearch.client.ml.inference.trainedmodel.ensemble;

import org.elasticsearch.client.ml.inference.NamedXContentObject;

public interface OutputAggregator extends NamedXContentObject {
    /**
     * @return The name of the output aggregator
     */
    String getName();
}
