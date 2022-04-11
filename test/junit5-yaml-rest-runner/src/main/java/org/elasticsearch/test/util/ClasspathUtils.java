/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.util;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public abstract class ClasspathUtils {
    public static Path[] findFilePaths(ClassLoader classLoader, String path) {
        List<Path> paths = new ArrayList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(path);

            while (resources.hasMoreElements()) {
                URI uri = resources.nextElement().toURI();
                if (uri.getScheme().equalsIgnoreCase("file")) {
                    paths.add(Paths.get(uri));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return paths.toArray(new Path[] {});
    }
}
