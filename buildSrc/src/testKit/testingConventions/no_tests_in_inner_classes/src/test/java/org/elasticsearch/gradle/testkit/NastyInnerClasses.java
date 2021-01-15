/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * and the Server Side Public License, v 1; you may not use this file except in
 * compliance with, at your election, the Elastic License or the Server Side
 * Public License, v 1.
 */
package org.elasticsearch.gradle.testkit;

import org.junit.Assert;
import org.junit.Test;

public class NastyInnerClasses {

    public static class NamingConventionTests {

    }

    public static class NamingConventionIT {

    }

    public static class LooksLikeATestWithoutNamingConvention1 {
        @Test
        public void annotatedTestMethod() {

        }
    }

    public static class LooksLikeATestWithoutNamingConvention2 extends Assert {

    }

    public static class LooksLikeATestWithoutNamingConvention3 {

        public void testMethod() {

        }

    }

    static abstract public class NonOffendingAbstractTests {

    }

    private static class NonOffendingPrivateTests {

    }

    static class NonOffendingPackageTests {

    }
}
