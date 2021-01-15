/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.lucene.BytesRefs;
import org.elasticsearch.common.lucene.search.AutomatonQueries;
import org.elasticsearch.index.query.SearchExecutionContext;

import java.util.List;
import java.util.Map;

/** Base {@link MappedFieldType} implementation for a field that is indexed
 *  with the inverted index. */
public abstract class TermBasedFieldType extends SimpleMappedFieldType {

    public TermBasedFieldType(String name, boolean isSearchable, boolean isStored, boolean hasDocValues,
                       TextSearchInfo textSearchInfo, Map<String, String> meta) {
        super(name, isSearchable, isStored, hasDocValues, textSearchInfo, meta);
    }

    /** Returns the indexed value used to construct search "values".
     *  This method is used for the default implementations of most
     *  query factory methods such as {@link #termQuery}. */
    protected BytesRef indexedValueForSearch(Object value) {
        return BytesRefs.toBytesRef(value);
    }

    @Override
    public Query termQueryCaseInsensitive(Object value, SearchExecutionContext context) {
        failIfNotIndexed();
        return AutomatonQueries.caseInsensitiveTermQuery(new Term(name(), indexedValueForSearch(value)));
    }

    @Override
    public Query termQuery(Object value, SearchExecutionContext context) {
        failIfNotIndexed();
        return new TermQuery(new Term(name(), indexedValueForSearch(value)));
    }

    @Override
    public Query termsQuery(List<?> values, SearchExecutionContext context) {
        failIfNotIndexed();
        BytesRef[] bytesRefs = new BytesRef[values.size()];
        for (int i = 0; i < bytesRefs.length; i++) {
            bytesRefs[i] = indexedValueForSearch(values.get(i));
        }
        return new TermInSetQuery(name(), bytesRefs);
    }

}
