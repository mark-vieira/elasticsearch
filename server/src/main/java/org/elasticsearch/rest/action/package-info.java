/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

/**
 * {@link org.elasticsearch.rest.RestHandler}s that translate requests from REST into internal requests and start them then wait for them to
 * complete and then translate them back into REST. And some classes to support them.
 */
package org.elasticsearch.rest.action;
