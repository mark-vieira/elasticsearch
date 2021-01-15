/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */
package org.elasticsearch.index.fielddata;


/**
 * {@link LeafFieldData} specialization for geo points.
 */
public interface LeafGeoPointFieldData extends LeafFieldData {

    /**
     * Return geo-point values.
     */
    MultiGeoPointValues getGeoPointValues();

}
