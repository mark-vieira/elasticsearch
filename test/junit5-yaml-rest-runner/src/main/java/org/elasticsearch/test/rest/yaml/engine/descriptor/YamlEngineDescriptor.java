/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.rest.yaml.engine.descriptor;

import org.elasticsearch.client.Request;
import org.elasticsearch.test.rest.yaml.ClientYamlTestCandidate;
import org.elasticsearch.test.rest.yaml.Features;
import org.elasticsearch.test.rest.yaml.engine.YamlRestTestExecutionContext;
import org.elasticsearch.test.rest.yaml.section.ClientYamlTestSuite;
import org.elasticsearch.test.rest.yaml.section.ExecutableSection;
import org.elasticsearch.test.util.Version;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.engine.support.hierarchical.Node;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.elasticsearch.test.util.XContentUtils.entityAsMap;

public class YamlEngineDescriptor extends EngineDescriptor implements Node<YamlRestTestExecutionContext> {

    public static final String ENGINE_ID = "elasticsearch-yaml-rest-engine";
    private static final String SUITE_SEGMENT_TYPE = "suite";

    private final Optional<String> suiteClassName;

    public YamlEngineDescriptor(UniqueId uniqueId) {
        super(uniqueId, "Yaml Test Engine");
        suiteClassName = uniqueId.getSegments().stream()
            .filter(s -> s.getType().equalsIgnoreCase(SUITE_SEGMENT_TYPE))
            .map(UniqueId.Segment::getValue)
            .findFirst();
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.SAME_THREAD;
    }

    public void addSuites(String folder, Set<Path> suites) {
        YamlDirectoryDescriptor directoryDescriptor = new YamlDirectoryDescriptor(
            getUniqueId().append(YamlDirectoryDescriptor.SEGMENT_TYPE, folder),
            folder
        );
        addFolder(directoryDescriptor, suites);
    }

    private void addFolder(YamlDirectoryDescriptor folder, Set<Path> suites) {
        suites.stream().map(path -> {
            try {
                return ClientYamlTestSuite.parse(ExecutableSection.XCONTENT_REGISTRY, folder.getDisplayName(), path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        })
            .sorted(Comparator.comparing(ClientYamlTestSuite::getName))
            .map(
                suite -> new YamlSuiteDescriptor(
                    suite,
                    folder.getUniqueId().append(YamlSuiteDescriptor.SEGMENT_TYPE, suite.getName()),
                    suite.getName()
                )
            )
            .forEach(suite -> addSuite(suite, folder));

        addChild(folder);
    }

    private void addSuite(YamlSuiteDescriptor suite, YamlDirectoryDescriptor parent) {
        parent.addChild(suite);

        ClientYamlTestSuite clientYamlTestSuite = suite.getClientYamlTestSuite();
        clientYamlTestSuite.getTestSections()
            .stream()
            .map(section -> new ClientYamlTestCandidate(clientYamlTestSuite, section))
            .map(
                candidate -> new YamlTestSectionDescriptor(
                    candidate,
                    suite.getUniqueId().append(YamlTestSectionDescriptor.SEGMENT_TYPE, candidate.getTestSection().getName()),
                    candidate.getTestPath(),
                    // Gradle test execution works based on class/method name so report that to the launcher
                    suiteClassName.map(suiteName -> MethodSource.from(suiteName, candidate.getTestPath())).orElse(null)
                )
            )
            .forEach(suite::addChild);
    }

    @Override
    public YamlRestTestExecutionContext prepare(YamlRestTestExecutionContext context) throws Exception {
        Map<?, ?> response = entityAsMap(context.client().performRequest(new Request("GET", "_nodes/plugins")));
        Map<?, ?> nodes = (Map<?, ?>) response.get("nodes");
        SortedSet<Version> nodeVersions = new TreeSet<>();
        for (Map.Entry<?, ?> node : nodes.entrySet()) {
            Map<?, ?> nodeInfo = (Map<?, ?>) node.getValue();
            nodeVersions.add(Version.fromString(nodeInfo.get("version").toString()));
            for (Object module : (List<?>) nodeInfo.get("modules")) {
                Map<?, ?> moduleInfo = (Map<?, ?>) module;
                final String moduleName = moduleInfo.get("name").toString();
                if (moduleName.startsWith("x-pack")) {
                    context.setHasXpack(true);
                    Features.hasXpack = true;
                }
                if (moduleName.equals("x-pack-ilm")) {
                    context.setHasIlm(true);
                }
                if (moduleName.equals("x-pack-rollup")) {
                    context.setHasRollups(true);
                }
                if (moduleName.equals("x-pack-ccr")) {
                    context.setHasCcr(true);
                }
                if (moduleName.equals("x-pack-shutdown")) {
                    context.setHasShutdown(true);
                }
            }
        }

        context.setNodeVersions(nodeVersions);

        return context;
    }
}
