/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.index.shard;

import org.elasticsearch.common.io.stream.StreamInput;

import java.io.IOException;

public class IndexShardRelocatedException extends IllegalIndexShardStateException {

    public IndexShardRelocatedException(ShardId shardId) {
        this(shardId, "Already relocated");
    }

    public IndexShardRelocatedException(ShardId shardId, String reason) {
        super(shardId, IndexShardState.STARTED, reason);
    }

    public IndexShardRelocatedException(StreamInput in) throws IOException{
        super(in);
    }
}
