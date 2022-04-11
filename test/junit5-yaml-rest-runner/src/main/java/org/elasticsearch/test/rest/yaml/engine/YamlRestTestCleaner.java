/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.rest.yaml.engine;

import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.test.util.SetOnce;
import org.elasticsearch.test.util.Version;
import org.elasticsearch.test.util.XContentUtils;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.elasticsearch.test.util.AssertUtils.assertAcked;
import static org.elasticsearch.test.util.AssertUtils.assertBusy;
import static org.elasticsearch.test.util.XContentUtils.convertToMap;
import static org.elasticsearch.test.util.XContentUtils.entityAsMap;
import static org.elasticsearch.test.util.XContentUtils.extractValue;
import static org.elasticsearch.test.util.XContentUtils.responseAsMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class YamlRestTestCleaner {
    private static final Logger LOGGER = LoggerFactory.getLogger(YamlRestTestCleaner.class);
    private static final Set<String> PRESERVED_ILM_POLICY_IDS = Set.of(
        "ilm-history-ilm-policy",
        "slm-history-ilm-policy",
        "watch-history-ilm-policy",
        "watch-history-ilm-policy-16",
        "ml-size-based-ilm-policy",
        "logs",
        "metrics",
        "synthetics",
        "7-days-default",
        "30-days-default",
        "90-days-default",
        "180-days-default",
        "365-days-default",
        ".fleet-actions-results-ilm-policy",
        ".deprecation-indexing-ilm-policy",
        ".monitoring-8-ilm-policy"
    );

    private final YamlRestTestExecutionContext context;

    public YamlRestTestCleaner(YamlRestTestExecutionContext context) {
        this.context = context;
    }

    public void ensureNoInitializingShards() throws IOException {
        Request request = new Request("GET", "/_cluster/health");
        request.addParameter("wait_for_no_initializing_shards", "true");
        request.addParameter("timeout", "70s");
        request.addParameter("level", "shards");
        context.client().performRequest(request);
    }

    public void wipeCluster() throws Exception {
        // Cleanup rollup before deleting indices. A rollup job might have bulks in-flight,
        // so we need to fully shut them down first otherwise a job might stall waiting
        // for a bulk to finish against a non-existing index (and then fail tests)
        // TODO: Skip this is preserveRollupJobsUponCompletion is set
        if (context.hasRollups()) {
            wipeRollupJobs();
            waitForPendingRollupTasks();
        }

        // TODO: Need to be able to configure "preserveSLMPoliciesUponCompletion"
        /*if (preserveSLMPoliciesUponCompletion() == false) {
            // Clean up SLM policies before trying to wipe snapshots so that no new ones get started by SLM after wiping
            deleteAllSLMPolicies();
        }*/

        // Clean up searchable snapshots indices before deleting snapshots and repositories
        // TODO: handle when preserveSearchableSnapshotsIndicesUponCompletion is set
        if (context.hasXpack() && context.getNodeVersions().first().onOrAfter(Version.fromString("7.8.0"))) {
            wipeSearchableSnapshotsIndices();
        }

        SetOnce<Map<String, List<Map<?, ?>>>> inProgressSnapshots = new SetOnce<>();
        // TODO:: handle this case
        // if (waitForAllSnapshotsWiped()) {
        if (false) {
            AtomicReference<Map<String, List<Map<?, ?>>>> snapshots = new AtomicReference<>();
            try {
                // Repeatedly delete the snapshots until there aren't any
                assertBusy(() -> {
                    snapshots.set(wipeSnapshots());
                    assertThat(snapshots.get(), anEmptyMap());
                }, 2, TimeUnit.MINUTES);
                // At this point there should be no snaphots
                inProgressSnapshots.set(snapshots.get());
            } catch (AssertionError e) {
                // This will cause an error at the end of this method, but do the rest of the cleanup first
                inProgressSnapshots.set(snapshots.get());
            }
        } else {
            inProgressSnapshots.set(wipeSnapshots());
        }

        // wipe data streams before indices so that the backing indices for data streams are handled properly
        // if (preserveDataStreamsUponCompletion() == false) {
        // TODO: handle preserveDataStreamsUponCompletion being set
        if (true) {
            wipeDataStreams();
        }

        // TODO handle preserveIndicesUponCompletion being set
        // if (preserveIndicesUponCompletion() == false) {
        if (true) {
            // wipe indices
            wipeAllIndices();
        }

        // wipe index templates
        // TODO handle preserveTemplatesUponCompletion
        // if (preserveTemplatesUponCompletion() == false) {
        if (true) {
            if (context.hasXpack()) {
                /*
                 * Delete only templates that xpack doesn't automatically
                 * recreate. Deleting them doesn't hurt anything, but it
                 * slows down the test because xpack will just recreate
                 * them.
                 */
                // In case of bwc testing, if all nodes are before 7.7.0 then no need to attempt to delete component and composable
                // index templates, because these were introduced in 7.7.0:
                if (context.getNodeVersions().stream().allMatch(version -> version.onOrAfter(Version.fromString("7.7.0")))) {
                    try {
                        Request getTemplatesRequest = new Request("GET", "_index_template");
                        Map<String, Object> composableIndexTemplates = convertToMap(
                            JsonXContent.jsonXContent,
                            EntityUtils.toString(context.client().performRequest(getTemplatesRequest).getEntity()),
                            false
                        );
                        List<String> names = ((List<?>) composableIndexTemplates.get("index_templates")).stream()
                            .map(ct -> (String) ((Map<?, ?>) ct).get("name"))
                            .filter(name -> isXPackTemplate(name) == false)
                            .collect(Collectors.toList());
                        if (names.isEmpty() == false) {
                            // Ideally we would want to check the version of the elected master node and
                            // send the delete request directly to that node.
                            if (context.getNodeVersions().stream().allMatch(version -> version.onOrAfter(Version.fromString("7.13.0")))) {
                                try {
                                    context.client().performRequest(new Request("DELETE", "_index_template/" + String.join(",", names)));
                                } catch (ResponseException e) {
                                    LOGGER.warn(e, () -> "unable to remove multiple composable index templates " + names);
                                }
                            } else {
                                for (String name : names) {
                                    try {
                                        context.client().performRequest(new Request("DELETE", "_index_template/" + name));
                                    } catch (ResponseException e) {
                                        LOGGER.warn(e, () -> "unable to remove composable index template " + name);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.debug(e, () -> "ignoring exception removing all composable index templates");
                        // We hit a version of ES that doesn't support index templates v2 yet, so it's safe to ignore
                    }
                    try {
                        Request compReq = new Request("GET", "_component_template");
                        String componentTemplates = EntityUtils.toString(context.client().performRequest(compReq).getEntity());
                        Map<String, Object> cTemplates = convertToMap(JsonXContent.jsonXContent, componentTemplates, false);
                        List<String> names = ((List<?>) cTemplates.get("component_templates")).stream()
                            .map(ct -> (String) ((Map<?, ?>) ct).get("name"))
                            .filter(name -> isXPackTemplate(name) == false)
                            .collect(Collectors.toList());
                        if (names.isEmpty() == false) {
                            // Ideally we would want to check the version of the elected master node and
                            // send the delete request directly to that node.
                            if (context.getNodeVersions().stream().allMatch(version -> version.onOrAfter(Version.fromString("7.13.0")))) {
                                try {
                                    context.client()
                                        .performRequest(new Request("DELETE", "_component_template/" + String.join(",", names)));
                                } catch (ResponseException e) {
                                    LOGGER.warn(e, () -> "unable to remove multiple component templates " + names);
                                }
                            } else {
                                for (String componentTemplate : names) {
                                    try {
                                        context.client().performRequest(new Request("DELETE", "_component_template/" + componentTemplate));
                                    } catch (ResponseException e) {
                                        LOGGER.warn(e, () -> "unable to remove component template " + componentTemplate);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.debug(e, () -> "ignoring exception removing all component templates");
                        // We hit a version of ES that doesn't support index templates v2 yet, so it's safe to ignore
                    }
                }
                // Always check for legacy templates:
                Request getLegacyTemplatesRequest = new Request("GET", "_template");
                Map<String, Object> legacyTemplates = convertToMap(
                    JsonXContent.jsonXContent,
                    EntityUtils.toString(context.client().performRequest(getLegacyTemplatesRequest).getEntity()),
                    false
                );
                for (String name : legacyTemplates.keySet()) {
                    if (isXPackTemplate(name)) {
                        continue;
                    }
                    try {
                        context.client().performRequest(new Request("DELETE", "_template/" + name));
                    } catch (ResponseException e) {
                        LOGGER.debug(e, () -> "unable to remove index template " + name);
                    }
                }
            } else {
                LOGGER.debug(() -> "Clearing all templates");
                context.client().performRequest(new Request("DELETE", "_template/*"));
                try {
                    context.client().performRequest(new Request("DELETE", "_index_template/*"));
                    context.client().performRequest(new Request("DELETE", "_component_template/*"));
                } catch (ResponseException e) {
                    // We hit a version of ES that doesn't support index templates v2 yet, so it's safe to ignore
                }
            }
        }

        // wipe cluster settings
        // if (preserveClusterSettings() == false) {
        // TODO: handle preserveClusterSettings
        if (true) {
            wipeClusterSettings();
        }

        // TODO: handle preserveILMPoliciesUponCompletion
        // if (hasIlm && false == preserveILMPoliciesUponCompletion()) {
        if (context.hasIlm()) {
            deleteAllILMPolicies(PRESERVED_ILM_POLICY_IDS);
        }

        // TODO: handle preserveAutoFollowPatternsUponCompletion
        // if (hasCcr && false == preserveAutoFollowPatternsUponCompletion()) {
        if (context.hasCcr()) {
            deleteAllAutoFollowPatterns();
        }

        deleteAllNodeShutdownMetadata();

        assertThat("Found in progress snapshots [" + inProgressSnapshots.get() + "].", inProgressSnapshots.get(), anEmptyMap());
    }

    private void wipeRollupJobs() throws IOException {
        final Response response;
        try {
            response = context.client().performRequest(new Request("GET", "/_rollup/job/_all"));
        } catch (ResponseException e) {
            // If we don't see the rollup endpoint (possibly because of running against an older ES version) we just bail
            if (e.getResponse().getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return;
            }
            throw e;
        }
        Map<String, Object> jobs = entityAsMap(response);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> jobConfigs = (List<Map<String, Object>>) extractValue("jobs", jobs);

        if (jobConfigs == null) {
            return;
        }

        for (Map<String, Object> jobConfig : jobConfigs) {
            @SuppressWarnings("unchecked")
            String jobId = (String) ((Map<String, Object>) jobConfig.get("config")).get("id");
            Request request = new Request("POST", "/_rollup/job/" + jobId + "/_stop");
            request.addParameter("ignore", "404");
            request.addParameter("wait_for_completion", "true");
            request.addParameter("timeout", "10s");
            LOGGER.debug(() -> "stopping rollup job [ " + jobId + "]");
            context.client().performRequest(request);
        }

        for (Map<String, Object> jobConfig : jobConfigs) {
            @SuppressWarnings("unchecked")
            String jobId = (String) ((Map<String, Object>) jobConfig.get("config")).get("id");
            Request request = new Request("DELETE", "/_rollup/job/" + jobId);
            request.addParameter("ignore", "404"); // Ignore 404s because they imply someone was racing us to delete this
            LOGGER.debug(() -> "deleting rollup job [" + jobId + "]");
            context.client().performRequest(request);
        }
    }

    private void waitForPendingRollupTasks() throws Exception {
        waitForPendingTasks(context.client(), taskName -> taskName.startsWith("xpack/rollup/job") == false);
    }

    private static void waitForPendingTasks(final RestClient restClient, final Predicate<String> taskFilter) throws Exception {
        assertBusy(() -> {
            try {
                final Request request = new Request("GET", "/_cat/tasks");
                request.addParameter("detailed", "true");
                final Response response = restClient.performRequest(request);
                /*
                 * Check to see if there are outstanding tasks; we exclude the list task itself, and any expected outstanding tasks using
                 * the specified task filter.
                 */
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    try (
                        BufferedReader responseReader = new BufferedReader(
                            new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8)
                        )
                    ) {
                        int activeTasks = 0;
                        String line;
                        final StringBuilder tasksListString = new StringBuilder();
                        while ((line = responseReader.readLine()) != null) {
                            final String taskName = line.split("\\s+")[0];
                            if (taskName.startsWith("cluster:monitor/tasks/lists") || taskFilter.test(taskName)) {
                                continue;
                            }
                            activeTasks++;
                            tasksListString.append(line);
                            tasksListString.append('\n');
                        }
                        assertEquals(0, activeTasks, activeTasks + " active tasks found:\n" + tasksListString);
                    }
                }
            } catch (final IOException e) {
                throw new AssertionError("error getting active tasks list", e);
            }
        }, 30L, TimeUnit.SECONDS);
    }

    private void wipeSearchableSnapshotsIndices() throws IOException {
        // retrieves all indices with a type of store equals to "snapshot"
        final Request request = new Request("GET", "_cluster/state/metadata");
        request.addParameter("filter_path", "metadata.indices.*.settings.index.store.snapshot");

        final Response response = context.client().performRequest(request);
        @SuppressWarnings("unchecked")
        Map<String, ?> indices = (Map<String, ?>) extractValue("metadata.indices", entityAsMap(response));
        if (indices != null) {
            for (String index : indices.keySet()) {
                try {
                    assertAcked(
                        "Failed to delete searchable snapshot index [" + index + ']',
                        context.client().performRequest(new Request("DELETE", index))
                    );
                } catch (ResponseException e) {
                    if (e.getResponse().getStatusLine().getStatusCode() != HttpStatus.SC_NOT_FOUND) {
                        throw e;
                    }
                }
            }
        }
    }

    private Map<String, List<Map<?, ?>>> wipeSnapshots() throws IOException {
        final Map<String, List<Map<?, ?>>> inProgressSnapshots = new HashMap<>();
        for (Map.Entry<String, ?> repo : entityAsMap(context.client().performRequest(new Request("GET", "/_snapshot/_all"))).entrySet()) {
            String repoName = repo.getKey();
            Map<?, ?> repoSpec = (Map<?, ?>) repo.getValue();
            String repoType = (String) repoSpec.get("type");
            // TODO: handle preserveSnapshotsUponCompletion being set
            // if (false == preserveSnapshotsUponCompletion() && repoType.equals("fs")) {
            if (false == false && repoType.equals("fs")) {
                // All other repo types we really don't have a chance of being able to iterate properly, sadly.
                Request listRequest = new Request("GET", "/_snapshot/" + repoName + "/_all");
                listRequest.addParameter("ignore_unavailable", "true");

                List<?> snapshots = (List<?>) entityAsMap(context.client().performRequest(listRequest)).get("snapshots");
                for (Object snapshot : snapshots) {
                    Map<?, ?> snapshotInfo = (Map<?, ?>) snapshot;
                    String name = (String) snapshotInfo.get("snapshot");
                    if (((String) snapshotInfo.get("state")).equalsIgnoreCase("IN_PROGRESS")) {
                        inProgressSnapshots.computeIfAbsent(repoName, key -> new ArrayList<>()).add(snapshotInfo);
                    }
                    LOGGER.debug(() -> "wiping snapshot [" + repoName + "/" + name + "]");
                    context.client().performRequest(new Request("DELETE", "/_snapshot/" + repoName + "/" + name));
                }
            }
            // TODO: handle preserveReposUponCompletion being set
            // if (preserveReposUponCompletion() == false) {
            if (true) {
                LOGGER.debug(() -> "wiping snapshot repository [" + repoName + "]");
                context.client().performRequest(new Request("DELETE", "_snapshot/" + repoName));
            }
        }
        return inProgressSnapshots;
    }

    private void wipeDataStreams() throws IOException {
        try {
            if (context.hasXpack()) {
                context.client().performRequest(new Request("DELETE", "_data_stream/*?expand_wildcards=all"));
            }
        } catch (ResponseException e) {
            // We hit a version of ES that doesn't understand expand_wildcards, try again without it
            try {
                if (context.hasXpack()) {
                    context.client().performRequest(new Request("DELETE", "_data_stream/*"));
                }
            } catch (ResponseException ee) {
                // We hit a version of ES that doesn't serialize DeleteDataStreamAction.Request#wildcardExpressionsOriginallySpecified field
                // or that doesn't support data streams so it's safe to ignore
                int statusCode = ee.getResponse().getStatusLine().getStatusCode();
                if (statusCode < 404 || statusCode > 405) {
                    throw ee;
                }
            }
        }
    }

    private void wipeAllIndices() throws IOException {
        boolean includeHidden = context.getNodeVersions().first().onOrAfter(Version.fromString("7.7.0"));
        try {
            // remove all indices except ilm history which can pop up after deleting all data streams but shouldn't interfere
            final Request deleteRequest = new Request("DELETE", "*,-.ds-ilm-history-*");
            deleteRequest.addParameter("expand_wildcards", "open,closed" + (includeHidden ? ",hidden" : ""));
            RequestOptions allowSystemIndexAccessWarningOptions = RequestOptions.DEFAULT.toBuilder().setWarningsHandler(warnings -> {
                if (warnings.size() == 0) {
                    return false;
                } else if (warnings.size() > 1) {
                    return true;
                }
                // We don't know exactly which indices we're cleaning up in advance, so just accept all system index access warnings.
                final String warning = warnings.get(0);
                final boolean isSystemIndexWarning = warning.contains("this request accesses system indices")
                    && warning.contains("but in a future major version, direct access to system indices will be prevented by default");
                return isSystemIndexWarning == false;
            }).build();
            deleteRequest.setOptions(allowSystemIndexAccessWarningOptions);
            final Response response = context.client().performRequest(deleteRequest);
            try (InputStream is = response.getEntity().getContent()) {
                assertTrue((boolean) convertToMap(XContentType.JSON.xContent(), is, true).get("acknowledged"));
            }
        } catch (ResponseException e) {
            // 404 here just means we had no indexes
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                throw e;
            }
        }
    }

    private void wipeClusterSettings() throws IOException {
        Map<?, ?> getResponse = entityAsMap(context.client().performRequest(new Request("GET", "/_cluster/settings")));

        boolean mustClear = false;
        XContentBuilder clearCommand = JsonXContent.contentBuilder();
        clearCommand.startObject();
        for (Map.Entry<?, ?> entry : getResponse.entrySet()) {
            String type = entry.getKey().toString();
            Map<?, ?> settings = (Map<?, ?>) entry.getValue();
            if (settings.isEmpty()) {
                continue;
            }
            mustClear = true;
            clearCommand.startObject(type);
            for (Object key : settings.keySet()) {
                clearCommand.field(key + ".*").nullValue();
            }
            clearCommand.endObject();
        }
        clearCommand.endObject();

        if (mustClear) {
            Request request = new Request("PUT", "/_cluster/settings");

            request.setOptions(RequestOptions.DEFAULT.toBuilder().setWarningsHandler(warnings -> {
                if (warnings.isEmpty()) {
                    return false;
                } else if (warnings.size() > 1) {
                    return true;
                } else {
                    return warnings.get(0).contains("xpack.monitoring") == false;
                }
            }));

            request.setJsonEntity(XContentUtils.toString(clearCommand));
            context.client().performRequest(request);
        }
    }

    private void deleteAllILMPolicies(Set<String> exclusions) throws IOException {
        Map<String, Object> policies;

        try {
            Response response = context.client().performRequest(new Request("GET", "/_ilm/policy"));
            policies = entityAsMap(response);
        } catch (ResponseException e) {
            if (HttpStatus.SC_METHOD_NOT_ALLOWED == e.getResponse().getStatusLine().getStatusCode()
                || HttpStatus.SC_BAD_REQUEST == e.getResponse().getStatusLine().getStatusCode()) {
                // If bad request returned, ILM is not enabled.
                return;
            }
            throw e;
        }

        if (policies == null || policies.isEmpty()) {
            return;
        }

        policies.keySet().stream().filter(p -> exclusions.contains(p) == false).forEach(policyName -> {
            try {
                context.client().performRequest(new Request("DELETE", "/_ilm/policy/" + policyName));
            } catch (IOException e) {
                throw new RuntimeException("failed to delete policy: " + policyName, e);
            }
        });
    }

    private void deleteAllAutoFollowPatterns() throws IOException {
        final List<Map<?, ?>> patterns;

        try {
            Response response = context.client().performRequest(new Request("GET", "/_ccr/auto_follow"));
            patterns = (List<Map<?, ?>>) entityAsMap(response).get("patterns");
        } catch (ResponseException e) {
            if (HttpStatus.SC_METHOD_NOT_ALLOWED == e.getResponse().getStatusLine().getStatusCode()
                || HttpStatus.SC_BAD_REQUEST == e.getResponse().getStatusLine().getStatusCode()) {
                // If bad request returned, CCR is not enabled.
                return;
            }
            throw e;
        }

        if (patterns == null || patterns.isEmpty()) {
            return;
        }

        for (Map<?, ?> pattern : patterns) {
            String patternName = (String) pattern.get("name");
            context.client().performRequest(new Request("DELETE", "/_ccr/auto_follow/" + patternName));
        }
    }

    private void deleteAllNodeShutdownMetadata() throws IOException {
        if (context.hasShutdown() == false || context.getNodeVersions().first().before(Version.fromString("7.15.0"))) {
            // Node shutdown APIs are only present in xpack
            return;
        }
        Request getShutdownStatus = new Request("GET", "_nodes/shutdown");
        Map<String, Object> statusResponse = responseAsMap(context.client().performRequest(getShutdownStatus));
        List<Map<String, Object>> nodesArray = (List<Map<String, Object>>) statusResponse.get("nodes");
        List<String> nodeIds = nodesArray.stream().map(nodeShutdownMetadata -> (String) nodeShutdownMetadata.get("node_id")).toList();
        for (String nodeId : nodeIds) {
            Request deleteRequest = new Request("DELETE", "_nodes/" + nodeId + "/shutdown");
            Response response = context.client().performRequest(deleteRequest);
            assertThat(response.getStatusLine().getStatusCode(), anyOf(equalTo(200), equalTo(201)));
        }
    }

    private static boolean isXPackTemplate(String name) {
        if (name.startsWith(".monitoring-")) {
            return true;
        }
        if (name.startsWith(".watch") || name.startsWith(".triggered_watches")) {
            return true;
        }
        if (name.startsWith(".data-frame-")) {
            return true;
        }
        if (name.startsWith(".ml-")) {
            return true;
        }
        if (name.startsWith(".transform-")) {
            return true;
        }
        if (name.startsWith(".deprecation-")) {
            return true;
        }
        switch (name) {
            case ".watches":
            case "security_audit_log":
            case ".slm-history":
            case ".async-search":
            case "saml-service-provider":
            case "logs":
            case "logs-settings":
            case "logs-mappings":
            case "metrics":
            case "metrics-settings":
            case "metrics-mappings":
            case "synthetics":
            case "synthetics-settings":
            case "synthetics-mappings":
            case ".snapshot-blob-cache":
            case "ilm-history":
            case "logstash-index-template":
            case "security-index-template":
            case "data-streams-mappings":
                return true;
            default:
                return false;
        }
    }
}
