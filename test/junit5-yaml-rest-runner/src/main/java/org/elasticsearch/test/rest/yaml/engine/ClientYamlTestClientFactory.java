/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.rest.yaml.engine;

import org.apache.http.HttpHost;
import org.elasticsearch.client.Node;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.sniff.ElasticsearchNodesSniffer;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.test.rest.yaml.ClientYamlTestClient;
import org.elasticsearch.test.rest.yaml.ClientYamlTestResponse;
import org.elasticsearch.test.rest.yaml.restspec.ClientYamlSuiteRestApi;
import org.elasticsearch.test.rest.yaml.restspec.ClientYamlSuiteRestSpec;
import org.elasticsearch.test.util.ConfigUtils;
import org.elasticsearch.test.util.Version;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.util.Collections.unmodifiableList;
import static org.elasticsearch.test.util.XContentUtils.extractValue;

public class ClientYamlTestClientFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientYamlTestClientFactory.class);
    private static final String SPEC_PATH = "rest-api-spec/api";
    private static final String REST_TESTS_VALIDATE_SPEC = "tests.rest.validate_spec";

    public ClientYamlTestClient buildClient() {
        ClientYamlSuiteRestSpec restSpec = ClientYamlSuiteRestSpec.load(SPEC_PATH);
        validateSpec(restSpec);
        List<HttpHost> hosts = getClusterHosts();
        RestClient client = buildClient(hosts.toArray(new HttpHost[0]));
        Tuple<Version, Version> versionVersionTuple = readVersionsFromCatNodes(client);
        final Version esVersion = versionVersionTuple.v1();
        final Version masterVersion = versionVersionTuple.v2();
        final String os = readOsFromNodesInfo(client);

        LOGGER.info(
            () -> "initializing client, minimum es version ["
                + esVersion
                + "], master version, ["
                + masterVersion
                + "], hosts "
                + hosts
                + " , os ["
                + os
                + "]"
        );

        return new ClientYamlTestClient(
            restSpec,
            client,
            hosts,
            esVersion,
            masterVersion,
            os,
            () -> getClientBuilderWithSniffedHosts(client)
        );
    }

    private void validateSpec(ClientYamlSuiteRestSpec restSpec) {
        boolean validateSpec = ConfigUtils.getBooleanProperty(REST_TESTS_VALIDATE_SPEC, true);
        if (validateSpec) {
            StringBuilder errorMessage = new StringBuilder();
            for (ClientYamlSuiteRestApi restApi : restSpec.getApis()) {
                if (restApi.isBodySupported()) {
                    for (ClientYamlSuiteRestApi.Path path : restApi.getPaths()) {
                        List<String> methodsList = Arrays.asList(path.methods());
                        if (methodsList.contains("GET") && restApi.isBodySupported()) {
                            if (methodsList.contains("POST") == false) {
                                errorMessage.append("\n- ")
                                    .append(restApi.getName())
                                    .append(" supports GET with a body but doesn't support POST");
                            }
                        }
                    }
                }
            }
            if (errorMessage.length() > 0) {
                throw new IllegalArgumentException(errorMessage.toString());
            }
        }
    }

    private List<HttpHost> getClusterHosts() {
        String cluster = getTestRestCluster();
        String[] stringUrls = cluster.split(",");
        List<HttpHost> hosts = new ArrayList<>(stringUrls.length);
        for (String stringUrl : stringUrls) {
            int portSeparator = stringUrl.lastIndexOf(':');
            if (portSeparator < 0) {
                throw new IllegalArgumentException("Illegal cluster url [" + stringUrl + "]");
            }
            String host = stringUrl.substring(0, portSeparator);
            int port = Integer.parseInt(stringUrl.substring(portSeparator + 1));
            hosts.add(new HttpHost(host, port));
        }
        return unmodifiableList(hosts);
    }

    private String getTestRestCluster() {
        String cluster = System.getProperty("tests.rest.cluster");
        if (cluster == null) {
            throw new RuntimeException(
                "Must specify [tests.rest.cluster] system property with a comma delimited list of [host:port] "
                    + "to which to send REST requests"
            );
        }

        return cluster;
    }

    private RestClient buildClient(HttpHost[] hosts) {
        RestClientBuilder builder = RestClient.builder(hosts);
        builder.setRequestConfigCallback(conf -> conf.setSocketTimeout(60000));
        builder.setStrictDeprecationMode(true);
        return builder.build();
    }

    private Tuple<Version, Version> readVersionsFromCatNodes(RestClient restClient) {
        // we simply go to the _cat/nodes API and parse all versions in the cluster
        final Request request = new Request("GET", "/_cat/nodes");
        request.addParameter("h", "version,master");
        request.setOptions(RequestOptions.DEFAULT);
        Response response;
        ClientYamlTestResponse restTestResponse;
        try {
            response = restClient.performRequest(request);
            restTestResponse = new ClientYamlTestResponse(response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String nodesCatResponse = restTestResponse.getBodyAsString();
        String[] split = nodesCatResponse.split("\n");
        Version version = null;
        Version masterVersion = null;
        for (String perNode : split) {
            final String[] versionAndMaster = perNode.split("\\s+");
            assert versionAndMaster.length == 2 : "invalid line: " + perNode + " length: " + versionAndMaster.length;
            final Version currentVersion = Version.fromString(versionAndMaster[0]);
            final boolean master = versionAndMaster[1].trim().equals("*");
            if (master) {
                assert masterVersion == null;
                masterVersion = currentVersion;
            }
            if (version == null) {
                version = currentVersion;
            } else if (version.onOrAfter(currentVersion)) {
                version = currentVersion;
            }
        }
        return new Tuple<>(version, masterVersion);
    }

    private String readOsFromNodesInfo(RestClient restClient) {
        final Request request = new Request("GET", "/_nodes/os");
        Response response;
        ClientYamlTestResponse restTestResponse;
        try {
            response = restClient.performRequest(request);
            restTestResponse = new ClientYamlTestResponse(response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SortedSet<String> osPrettyNames = new TreeSet<>();

        @SuppressWarnings("unchecked")
        final Map<String, Object> nodes;
        try {
            nodes = (Map<String, Object>) restTestResponse.evaluate("nodes");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (Map.Entry<String, Object> node : nodes.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nodeInfo = (Map<String, Object>) node.getValue();

            osPrettyNames.add((String) extractValue("os.pretty_name", nodeInfo));
        }

        assert osPrettyNames.isEmpty() == false : "no os found";

        // Although in theory there should only be one element as all nodes are running on the same machine,
        // in reality there can be two in mixed version clusters if different Java versions report the OS
        // name differently. This has been observed to happen on Windows, where Java needs to be updated to
        // recognize new Windows versions, and until this update has been done the newest version of Windows
        // is reported as the previous one. In this case taking the last alphabetically is likely to be most
        // accurate, for example if "Windows Server 2016" and "Windows Server 2019" are reported by different
        // Java versions then Windows Server 2019 is likely to be correct.
        return osPrettyNames.last();
    }



    /**
     * Sniff the cluster for host metadata and return a
     * {@link RestClientBuilder} for a client with that metadata.
     */
    private static RestClientBuilder getClientBuilderWithSniffedHosts(RestClient client) {
        ElasticsearchNodesSniffer.Scheme scheme = ElasticsearchNodesSniffer.Scheme.HTTP;
        ElasticsearchNodesSniffer sniffer = new ElasticsearchNodesSniffer(
            client,
            ElasticsearchNodesSniffer.DEFAULT_SNIFF_REQUEST_TIMEOUT,
            scheme
        );
        Node[] nodes;
        try {
            nodes = sniffer.sniff().toArray(new Node[0]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return RestClient.builder(nodes);
    }
}
