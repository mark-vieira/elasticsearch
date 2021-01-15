/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.index.store;

import org.elasticsearch.common.settings.Settings;


public class SmbMMapFsTests extends AbstractAzureFsTestCase {

    @Override
    public Settings indexSettings() {
        return Settings.builder()
                .put(super.indexSettings())
                .put("index.store.type", "smb_mmap_fs")
                .build();
    }

}
