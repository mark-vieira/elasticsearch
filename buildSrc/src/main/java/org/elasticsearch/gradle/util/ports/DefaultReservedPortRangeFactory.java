/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.gradle.util.ports;

public class DefaultReservedPortRangeFactory implements ReservedPortRangeFactory {
    @Override
    public ReservedPortRange getReservedPortRange(int startPort, int endPort) {
        return new ReservedPortRange(startPort, endPort);
    }

}
