/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.rest.yaml.engine;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.elasticsearch.client.NodeSelector;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.test.Stash;
import org.elasticsearch.test.rest.yaml.ClientYamlTestCandidate;
import org.elasticsearch.test.rest.yaml.ClientYamlTestClient;
import org.elasticsearch.test.rest.yaml.ClientYamlTestResponse;
import org.elasticsearch.test.rest.yaml.ClientYamlTestResponseException;
import org.elasticsearch.test.util.Version;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentType;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.engine.support.hierarchical.EngineExecutionContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

public class YamlRestTestExecutionContext implements EngineExecutionContext {
    private static final Logger logger = LoggerFactory.getLogger(YamlRestTestExecutionContext.class);
    private static final XContentType[] STREAMING_CONTENT_TYPES = new XContentType[] { XContentType.JSON, XContentType.SMILE };

    private final ClientYamlTestClient clientYamlTestClient;

    private Stash stash;
    private ClientYamlTestCandidate clientYamlTestCandidate;
    private ClientYamlTestResponse response;

    private boolean hasXpack;
    private boolean hasIlm;
    private boolean hasRollups;
    private boolean hasCcr;
    private boolean hasShutdown;
    private SortedSet<Version> nodeVersions;

    public YamlRestTestExecutionContext(ClientYamlTestClient clientYamlTestClient) {
        this(clientYamlTestClient, null, null, false, false, false, false, false, new TreeSet<>());
    }

    private YamlRestTestExecutionContext(
        ClientYamlTestClient clientYamlTestClient,
        ClientYamlTestCandidate clientYamlTestCandidate,
        Stash stash,
        boolean hasXpack,
        boolean hasIlm,
        boolean hasRollups,
        boolean hasCcr,
        boolean hasShutdown,
        SortedSet<Version> nodeVersions
    ) {
        this.clientYamlTestClient = clientYamlTestClient;
        this.clientYamlTestCandidate = clientYamlTestCandidate;
        this.stash = stash;
        this.hasXpack = hasXpack;
        this.hasIlm = hasIlm;
        this.hasRollups = hasRollups;
        this.hasCcr = hasCcr;
        this.hasShutdown = hasShutdown;
        this.nodeVersions = nodeVersions;
    }

    public YamlRestTestExecutionContext build(ClientYamlTestCandidate clientYamlTestCandidate) {
        return new YamlRestTestExecutionContext(
            clientYamlTestClient,
            clientYamlTestCandidate,
            new Stash(),
            hasXpack,
            hasIlm,
            hasRollups,
            hasCcr,
            hasShutdown,
            nodeVersions
        );
    }

    /**
     * Calls an elasticsearch api with the parameters and request body provided as arguments.
     * Saves the obtained response in the execution context.
     */
    public ClientYamlTestResponse callApi(
        String apiName,
        Map<String, String> params,
        List<Map<String, Object>> bodies,
        Map<String, String> headers
    ) throws IOException {
        return callApi(apiName, params, bodies, headers, NodeSelector.ANY);
    }

    /**
     * Calls an elasticsearch api with the parameters and request body provided as arguments.
     * Saves the obtained response in the execution context.
     */
    public ClientYamlTestResponse callApi(
        String apiName,
        Map<String, String> params,
        List<Map<String, Object>> bodies,
        Map<String, String> headers,
        NodeSelector nodeSelector
    ) throws IOException {
        // makes a copy of the parameters before modifying them for this specific request
        Map<String, String> requestParams = new HashMap<>(params);
        requestParams.putIfAbsent("error_trace", "true"); // By default ask for error traces, this my be overridden by params
        for (Map.Entry<String, String> entry : requestParams.entrySet()) {
            if (stash.containsStashedValue(entry.getValue())) {
                entry.setValue(stash.getValue(entry.getValue()).toString());
            }
        }

        // make a copy of the headers before modifying them for this specific request
        Map<String, String> requestHeaders = new HashMap<>(headers);
        for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
            if (stash.containsStashedValue(entry.getValue())) {
                entry.setValue(stash.getValue(entry.getValue()).toString());
            }
        }

        HttpEntity entity = createEntity(bodies, requestHeaders);
        try {
            response = callApiInternal(apiName, requestParams, entity, requestHeaders, nodeSelector);
            return response;
        } catch (ClientYamlTestResponseException e) {
            response = e.getRestTestResponse();
            throw e;
        } finally {
            // if we hit a bad exception the response is null
            Object responseBody = response != null ? response.getBody() : null;
            // we always stash the last response body
            stash.stashValue("body", responseBody);
            if (requestHeaders.isEmpty() == false) {
                stash.stashValue("request_headers", requestHeaders);
            }
        }
    }

    private HttpEntity createEntity(List<Map<String, Object>> bodies, Map<String, String> headers) throws IOException {
        if (bodies.isEmpty()) {
            return null;
        }
        if (bodies.size() == 1) {
            XContentType xContentType = getContentType(headers, XContentType.values());
            byte[] bytes = bodyAsBytes(bodies.get(0), xContentType);
            return new ByteArrayEntity(bytes, ContentType.create(xContentType.mediaTypeWithoutParameters(), StandardCharsets.UTF_8));
        } else {
            XContentType xContentType = getContentType(headers, STREAMING_CONTENT_TYPES);
            byte[][] bytesArray = new byte[bodies.size()][];
            int totalBytesLength = 0;
            for (int i = 0; i < bodies.size(); i++) {
                Map<String, Object> body = bodies.get(i);
                byte[] bytesRef = bodyAsBytes(body, xContentType);
                bytesArray[i] = bytesRef;
                totalBytesLength += bytesRef.length + 1;
            }
            ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[totalBytesLength]);
            for (byte[] bytesRef : bytesArray) {
                byteBuffer.put(bytesRef);
                byteBuffer.put(xContentType.xContent().streamSeparator());
            }
            return new ByteArrayEntity(
                byteBuffer.array(),
                ContentType.create(xContentType.mediaTypeWithoutParameters(), StandardCharsets.UTF_8)
            );
        }
    }

    private XContentType getContentType(Map<String, String> headers, XContentType[] supportedContentTypes) {
        XContentType xContentType = null;
        String contentType = headers.get("Content-Type");
        if (contentType != null) {
            xContentType = XContentType.fromMediaType(contentType);
        }
        if (xContentType != null) {
            return xContentType;
        }
        return XContentType.JSON;
    }

    private byte[] bodyAsBytes(Map<String, Object> bodyAsMap, XContentType xContentType) throws IOException {
        Map<String, Object> finalBodyAsMap = stash.replaceStashedValues(bodyAsMap);
        try (XContentBuilder builder = XContentFactory.contentBuilder(xContentType)) {
            XContentBuilder map = builder.map(finalBodyAsMap);
            map.close();
            return ((ByteArrayOutputStream) map.getOutputStream()).toByteArray();
        }
    }

    // pkg-private for testing
    ClientYamlTestResponse callApiInternal(
        String apiName,
        Map<String, String> params,
        HttpEntity entity,
        Map<String, String> headers,
        NodeSelector nodeSelector
    ) throws IOException {
        return clientYamlTestClient.callApi(apiName, params, entity, headers, nodeSelector);
    }

    /**
     * Extracts a specific value from the last saved response
     */
    public Object response(String path) throws IOException {
        return response.evaluate(path, stash);
    }

    /**
     * Clears the last obtained response and the stashed fields
     */
    public void clear() {
        logger.debug(() -> "resetting client, response and stash");
        response = null;
        stash.clear();
    }

    public Stash stash() {
        return stash;
    }

    /**
     * @return the version of the oldest node in the cluster
     */
    public Version esVersion() {
        return clientYamlTestClient.getEsVersion();
    }

    public Version masterVersion() {
        return clientYamlTestClient.getMasterVersion();
    }

    public String os() {
        return clientYamlTestClient.getOs();
    }

    public ClientYamlTestCandidate getClientYamlTestCandidate() {
        return clientYamlTestCandidate;
    }

    public RestClient client() {
        return clientYamlTestClient.getRestClient(NodeSelector.ANY);
    }

    public boolean hasXpack() {
        return hasXpack;
    }

    public void setHasXpack(boolean hasXpack) {
        this.hasXpack = hasXpack;
    }

    public boolean hasIlm() {
        return hasIlm;
    }

    public void setHasIlm(boolean hasIlm) {
        this.hasIlm = hasIlm;
    }

    public boolean hasRollups() {
        return hasRollups;
    }

    public void setHasRollups(boolean hasRollups) {
        this.hasRollups = hasRollups;
    }

    public boolean hasCcr() {
        return hasCcr;
    }

    public void setHasCcr(boolean hasCcr) {
        this.hasCcr = hasCcr;
    }

    public boolean hasShutdown() {
        return hasShutdown;
    }

    public void setHasShutdown(boolean hasShutdown) {
        this.hasShutdown = hasShutdown;
    }

    public SortedSet<Version> getNodeVersions() {
        return nodeVersions;
    }

    public void setNodeVersions(SortedSet<Version> nodeVersions) {
        this.nodeVersions = nodeVersions;
    }
}
