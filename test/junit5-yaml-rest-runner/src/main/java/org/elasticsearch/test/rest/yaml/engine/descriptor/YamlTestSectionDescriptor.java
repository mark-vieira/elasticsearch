/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.rest.yaml.engine.descriptor;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.WarningsHandler;
import org.elasticsearch.test.rest.yaml.ClientYamlTestCandidate;
import org.elasticsearch.test.rest.yaml.engine.YamlRestTestCleaner;
import org.elasticsearch.test.rest.yaml.engine.YamlRestTestExecutionContext;
import org.elasticsearch.test.rest.yaml.section.ExecutableSection;
import org.elasticsearch.test.util.XContentUtils;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;

public class YamlTestSectionDescriptor extends BaseYamlTestDescriptor {
    public static final String SEGMENT_TYPE = "test-section";
    private static final Logger LOGGER = LoggerFactory.getLogger(YamlTestSectionDescriptor.class);
    private static final String FIPS_SYSPROP = "tests.fips.enabled";

    private ClientYamlTestCandidate testCandidate;

    public YamlTestSectionDescriptor(
        ClientYamlTestCandidate clientYamlTestCandidate,
        UniqueId uniqueId,
        String displayName,
        TestSource source
    ) {
        super(uniqueId, displayName, source);
        this.testCandidate = clientYamlTestCandidate;
    }

    @Override
    public Type getType() {
        return Type.TEST;
    }

    public ClientYamlTestCandidate getClientYamlTestCandidate() {
        return testCandidate;
    }

    @Override
    public YamlRestTestExecutionContext prepare(YamlRestTestExecutionContext context) throws Exception {
        return super.prepare(context).build(testCandidate);
    }

    @Override
    public SkipResult shouldBeSkipped(YamlRestTestExecutionContext context) throws Exception {
        // skip test if the whole suite (yaml file) is disabled
        if (testCandidate.getSetupSection().getSkipSection().skip(context.esVersion())) {
            return SkipResult.skip(testCandidate.getSetupSection().getSkipSection().getSkipMessage(testCandidate.getSuitePath()));
        }

        // skip test if the whole suite (yaml file) is disabled
        if (testCandidate.getTeardownSection().getSkipSection().skip(context.esVersion())) {
            return SkipResult.skip(testCandidate.getTeardownSection().getSkipSection().getSkipMessage(testCandidate.getSuitePath()));
        }

        // skip test if test section is disabled
        if (testCandidate.getTestSection().getSkipSection().skip(context.esVersion())) {
            return SkipResult.skip(testCandidate.getTestSection().getSkipSection().getSkipMessage(testCandidate.getTestPath()));
        }

        // skip test if os is excluded
        if (testCandidate.getTestSection().getSkipSection().skip(context.os())) {
            return SkipResult.skip(testCandidate.getTestSection().getSkipSection().getSkipMessage(testCandidate.getTestPath()));
        }

        if (Boolean.parseBoolean(System.getProperty(FIPS_SYSPROP))
            && testCandidate.getTestSection().getSkipSection().getFeatures().contains("fips_140")) {
            return SkipResult.skip("[" + testCandidate.getTestPath() + "] skipped, reason: in fips 140 mode");
        }

        return super.shouldBeSkipped(context);
    }

    @Override
    public void after(YamlRestTestExecutionContext context) throws Exception {
        YamlRestTestCleaner cleaner = new YamlRestTestCleaner(context);
        cleaner.ensureNoInitializingShards();
        cleaner.wipeCluster();
    }

    @Override
    public YamlRestTestExecutionContext execute(YamlRestTestExecutionContext context, DynamicTestExecutor dynamicTestExecutor)
        throws Exception {

        // let's check that there is something to run, otherwise there might be a problem with the test section
        if (testCandidate.getTestSection().getExecutableSections().size() == 0) {
            throw new IllegalArgumentException("No executable sections loaded for [" + testCandidate.getTestPath() + "]");
        }

        if (testCandidate.getTestSection().getSkipSection().getFeatures().contains("default_shards") == false) {
            final Request request = new Request("PUT", "/_template/global");
            request.setJsonEntity("{\"index_patterns\":[\"*\"],\"settings\":{\"index.number_of_shards\":2}}");
            // Because this has not yet transitioned to a composable template, it's possible that
            // this can overlap an installed composable template since this is a global (*)
            // template. In order to avoid this failing the test, we override the warnings handler
            // to be permissive in this case. This can be removed once all tests use composable
            // templates instead of legacy templates
            RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
            builder.setWarningsHandler(WarningsHandler.PERMISSIVE);
            request.setOptions(builder.build());
            context.client().performRequest(request);
        }

        if (testCandidate.getSetupSection().isEmpty() == false) {
            LOGGER.debug(() -> "start setup test [" + testCandidate.getTestPath() + "]");
            for (ExecutableSection executableSection : testCandidate.getSetupSection().getExecutableSections()) {
                executeSection(context, executableSection);
            }
            LOGGER.debug(() -> "end setup test [" + testCandidate.getTestPath() + "]");
        }

        context.clear();

        try {
            for (ExecutableSection executableSection : testCandidate.getTestSection().getExecutableSections()) {
                executeSection(context, executableSection);
            }
        } finally {
            LOGGER.debug(() -> "start teardown test [" + testCandidate.getTestPath() + "]");
            for (ExecutableSection doSection : testCandidate.getTeardownSection().getDoSections()) {
                executeSection(context, doSection);
            }
            LOGGER.debug(() -> "end teardown test [" + testCandidate.getTestPath() + "]");
        }

        return context;
    }

    private void executeSection(YamlRestTestExecutionContext context, ExecutableSection executableSection) {
        try {
            executableSection.execute(context);
        } catch (AssertionError | Exception e) {
            // Dump the stash on failure. Instead of dumping it in true json we escape `\n`s so stack traces are easier to read
            LOGGER.info(
                () -> "Stash dump on test failure ["
                    + XContentUtils.toString(context.stash(), true, true).replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
                    + "]"
            );
            if (e instanceof AssertionError) {
                throw new AssertionError(errorMessage(executableSection, e), e);
            } else {
                throw new RuntimeException(errorMessage(executableSection, e), e);
            }
        }
    }

    private String errorMessage(ExecutableSection executableSection, Throwable t) {
        return "Failure at [" + testCandidate.getSuitePath() + ":" + executableSection.getLocation().lineNumber() + "]: " + t.getMessage();
    }
}
