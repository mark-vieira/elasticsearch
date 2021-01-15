/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.painless.spi;

import org.elasticsearch.script.ScriptContext;

import java.util.List;
import java.util.Map;

public interface PainlessExtension {

    Map<ScriptContext<?>, List<Whitelist>> getContextWhitelists();
}
