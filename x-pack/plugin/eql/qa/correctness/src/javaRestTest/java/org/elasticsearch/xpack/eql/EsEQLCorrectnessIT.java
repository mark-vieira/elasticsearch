/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.eql;

import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;
import com.carrotsearch.randomizedtesting.annotations.TimeoutSuite;
import org.apache.http.HttpHost;
import org.apache.lucene.util.TimeUnits;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequest;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotRequest;
import org.elasticsearch.client.HttpAsyncResponseConsumerFactory;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.eql.EqlSearchRequest;
import org.elasticsearch.client.eql.EqlSearchResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.elasticsearch.xpack.ql.TestUtils.assertNoSearchContexts;

@TimeoutSuite(millis = 60 * TimeUnits.MINUTE)
public class EsEQLCorrectnessIT extends ESRestTestCase {

    private static final String PARAM_FORMATTING = "%1$s";
    private static final String QUERIES_FILENAME = "queries.toml";
    private static final String PROPERTIES_FILENAME = "config.properties";

    private static RestHighLevelClient highLevelClient;
    private static Properties CFG;
    private static RequestOptions COMMON_REQUEST_OPTIONS;

    @BeforeClass
    public static void init() throws IOException {
        try (InputStream is = EsEQLCorrectnessIT.class.getClassLoader().getResourceAsStream(PROPERTIES_FILENAME)) {
            CFG = new Properties();
            CFG.load(is);
        }

        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        builder.setHttpAsyncResponseConsumerFactory(
            new HttpAsyncResponseConsumerFactory.HeapBufferedResponseConsumerFactory(1000 * 1024 * 1024)
        );
        COMMON_REQUEST_OPTIONS = builder.build();
    }

    @Before
    public void restoreDataFromGcsRepo() throws Exception {
        if (client().performRequest(new Request("HEAD", "/" + CFG.getProperty("index_name"))).getStatusLine().getStatusCode() == 404) {
            highLevelClient().snapshot()
                .createRepository(
                    new PutRepositoryRequest(CFG.getProperty("gcs_repo_name")).type("gcs")
                        .settings(
                            Settings.builder()
                                .put("bucket", CFG.getProperty("gcs_bucket_name"))
                                .put("base_path", CFG.getProperty("gcs_base_path"))
                                .put("client", CFG.getProperty("gcs_client_name"))
                                .build()
                        ),
                    RequestOptions.DEFAULT
                );
            highLevelClient().snapshot()
                .restore(
                    new RestoreSnapshotRequest(CFG.getProperty("gcs_repo_name"), CFG.getProperty("gcs_snapshot_name")).waitForCompletion(
                        true
                    ),
                    RequestOptions.DEFAULT
                );
        }
    }

    @After
    public void checkSearchContent() throws Exception {
        assertNoSearchContexts(client());
    }

    @AfterClass
    public static void wipeTestData() throws IOException {
        try {
            adminClient().performRequest(new Request("DELETE", "/*"));
        } catch (ResponseException e) {
            // 404 here just means we had no indexes
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                throw e;
            }
        }
    }

    @Override
    protected boolean preserveClusterUponCompletion() {
        // Need to preserve data between parameterized tests runs
        return true;
    }

    @Override
    protected RestClient buildClient(Settings settings, HttpHost[] hosts) throws IOException {
        RestClientBuilder builder = RestClient.builder(hosts);
        configureClient(builder, settings);
        builder.setRequestConfigCallback(
            requestConfigBuilder -> requestConfigBuilder.setConnectTimeout(30000000)
                .setConnectionRequestTimeout(30000000)
                .setSocketTimeout(30000000)
        );
        builder.setStrictDeprecationMode(true);
        return builder.build();
    }

    private EqlSpec spec;

    public EsEQLCorrectnessIT(EqlSpec spec) {
        this.spec = spec;
    }

    private RestHighLevelClient highLevelClient() {
        if (highLevelClient == null) {
            highLevelClient = new RestHighLevelClient(client(), ignore -> {}, Collections.emptyList()) {
            };
        }
        return highLevelClient;
    }

    @ParametersFactory(shuffle = false, argumentFormatting = PARAM_FORMATTING)
    public static Iterable<Object[]> parameters() throws Exception {
        Collection<EqlSpec> specs;
        try (InputStream is = EsEQLCorrectnessIT.class.getClassLoader().getResourceAsStream(QUERIES_FILENAME)) {
            specs = EqlSpecLoader.readFromStream(is);
        }
        assertFalse("Found 0 queries for testing", specs.isEmpty());

        List<Object[]> params = new ArrayList<>(specs.size());
        for (EqlSpec spec : specs) {
            params.add(new Object[] { spec });
        }
        return params;
    }

    public void test() throws Exception {
        long totalTime = 0;
        int queryNo = spec.queryNo();
        /* For debugging
        for (int i = 0; i < spec.filters().length; i++) {
            String filterQuery = spec.filters()[i];
            EqlSearchRequest eqlSearchRequest = new EqlSearchRequest(INDEX_NAME, filterQuery);
            eqlSearchRequest.eventCategoryField("event_type");
            eqlSearchRequest.size(100000);
            EqlSearchResponse response = client.eql().search(eqlSearchRequest, commonRequestOptions);
            assertEquals("Failed to match filter counts for query No: " + queryNo + " filterCount: " + i,
                    spec.filterCounts()[i], response.hits().events().size());
        } */

        EqlSearchRequest eqlSearchRequest = new EqlSearchRequest(CFG.getProperty("index_name"), spec.query());
        eqlSearchRequest.eventCategoryField("event_type");
        eqlSearchRequest.tiebreakerField("serial_id");
        eqlSearchRequest.size(Integer.parseInt(CFG.getProperty("size")));
        eqlSearchRequest.fetchSize(Integer.parseInt(CFG.getProperty("fetch_size")));
        EqlSearchResponse response = highLevelClient.eql().search(eqlSearchRequest, RequestOptions.DEFAULT);
        totalTime += response.took();
        assertEquals(
            "Failed to match sequence count for query No: " + queryNo + " : " + spec.query() + System.lineSeparator(),
            spec.seqCount(),
            response.hits().sequences().size()
        );
        int expectedEvenIdIdx = 0;
        for (EqlSearchResponse.Sequence seq : response.hits().sequences()) {
            for (EqlSearchResponse.Event event : seq.events()) {
                assertEquals(
                    "Failed to match event ids for query No: " + queryNo + " : " + spec.query() + System.lineSeparator(),
                    spec.expectedEventIds()[expectedEvenIdIdx++],
                    ((Integer) event.sourceAsMap().get("serial_id")).longValue()
                );
            }
        }
        // System.out.println("Total time: " + totalTime + "ms");
    }
}
