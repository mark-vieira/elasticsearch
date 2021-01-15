/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.transport;

import org.elasticsearch.cluster.node.DiscoveryNode;

public interface RemoteClusterAwareRequest {

    /**
     * Returns the preferred discovery node for this request. The remote cluster client will attempt to send
     * this request directly to this node. Otherwise, it will send the request as a proxy action that will
     * be routed by the remote cluster to this node.
     *
     * @return preferred discovery node
     */
    DiscoveryNode getPreferredTargetNode();

}
