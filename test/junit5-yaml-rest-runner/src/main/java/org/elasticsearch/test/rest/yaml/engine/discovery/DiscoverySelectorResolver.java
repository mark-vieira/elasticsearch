/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.rest.yaml.engine.discovery;

import org.elasticsearch.test.rest.yaml.engine.descriptor.YamlEngineDescriptor;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.discovery.ClasspathResourceSelector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.elasticsearch.test.util.ClasspathUtils.findFilePaths;

public class DiscoverySelectorResolver {
    private final EngineDiscoveryRequest request;

    public DiscoverySelectorResolver(EngineDiscoveryRequest request) {
        this.request = request;
    }

    public void resolve(YamlEngineDescriptor descriptor) {
        request.getSelectorsByType(ClasspathResourceSelector.class)
            .forEach(s -> loadSuites(s.getClasspathResourceName(), "").forEach(descriptor::addSuites));
    }

    private static Map<String, Set<Path>> loadSuites(String searchRoot, String... paths) {
        Map<String, Set<Path>> files = new HashMap<>();
        Path[] roots = findFilePaths(DiscoverySelectorResolver.class.getClassLoader(), searchRoot);
        for (Path root : roots) {
            for (String strPath : paths) {
                Path path = root.resolve(strPath);
                if (Files.isDirectory(path)) {
                    try (Stream<Path> stream = Files.walk(path)) {
                        stream.forEach(file -> {
                            if (file.toString().endsWith(".yml")) {
                                addSuite(root, file, files);
                            } else if (file.toString().endsWith(".yaml")) {
                                throw new IllegalArgumentException("yaml files are no longer supported: " + file);
                            }
                        });
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                } else {
                    path = root.resolve(strPath + ".yml");
                    assert Files.exists(path);
                    addSuite(root, path, files);
                }
            }
        }
        return files;
    }

    private static void addSuite(Path root, Path file, Map<String, Set<Path>> files) {
        String groupName = root.relativize(file.getParent()).toString();
        Set<Path> filesSet = files.computeIfAbsent(groupName, k -> new HashSet<>());
        filesSet.add(file);
        List<String> fileNames = filesSet.stream().map(p -> p.getFileName().toString()).toList();
        if (Collections.frequency(fileNames, file.getFileName().toString()) > 1) {
            Logger logger = LoggerFactory.getLogger(DiscoverySelectorResolver.class);
            logger.warn(
                () -> "Found duplicate test name ["
                    + groupName
                    + "/"
                    + file.getFileName()
                    + "] on the class path. "
                    + "This can result in class loader dependent execution commands and reproduction commands "
                    + "(will add #2 to one of the test names dependent on the classloading order)"
            );
        }
    }
}
