/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.test.util;

import org.elasticsearch.client.Response;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.xcontent.DeprecationHandler;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.junit.platform.commons.util.ExceptionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class XContentUtils {
    public static Map<String, Object> entityAsMap(Response response) throws IOException {
        XContentType xContentType = XContentType.fromMediaType(response.getEntity().getContentType().getValue());
        // EMPTY and THROW are fine here because `.map` doesn't use named x content or deprecation
        try (
            XContentParser parser = xContentType.xContent()
                .createParser(
                    XContentParserConfiguration.EMPTY.withRegistry(NamedXContentRegistry.EMPTY)
                        .withDeprecationHandler(DeprecationHandler.THROW_UNSUPPORTED_OPERATION),
                    response.getEntity().getContent()
                )
        ) {
            return parser.map();
        }
    }

    public static Object extractValue(String path, Map<?, ?> map) {
        return extractValue(map, path.split("\\."));
    }

    public static Object extractValue(Map<?, ?> map, String... pathElements) {
        if (pathElements.length == 0) {
            return null;
        }
        return extractValue(pathElements, 0, map, null);
    }

    private static Object extractValue(String[] pathElements, int index, Object currentValue, Object nullValue) {
        if (currentValue instanceof List<?> valueList) {
            List<Object> newList = new ArrayList<>(valueList.size());
            for (Object o : valueList) {
                Object listValue = extractValue(pathElements, index, o, nullValue);
                if (listValue != null) {
                    newList.add(listValue);
                }
            }
            return newList;
        }

        if (index == pathElements.length) {
            return currentValue != null ? currentValue : nullValue;
        }

        if (currentValue instanceof Map<?, ?> map) {
            String key = pathElements[index];
            int nextIndex = index + 1;
            List<Object> extractedValues = new ArrayList<>();
            while (true) {
                if (map.containsKey(key)) {
                    Object mapValue = map.get(key);
                    if (mapValue == null) {
                        extractedValues.add(nullValue);
                    } else {
                        Object val = extractValue(pathElements, nextIndex, mapValue, nullValue);
                        if (val != null) {
                            extractedValues.add(val);
                        }
                    }
                }
                if (nextIndex == pathElements.length) {
                    if (extractedValues.size() == 0) {
                        return null;
                    } else if (extractedValues.size() == 1) {
                        return extractedValues.get(0);
                    } else {
                        return extractedValues;
                    }
                }
                key += "." + pathElements[nextIndex];
                nextIndex++;
            }
        }
        return null;
    }

    public static Map<String, Object> responseAsMap(Response response) throws IOException {
        XContentType entityContentType = XContentType.fromMediaType(response.getEntity().getContentType().getValue());
        Map<String, Object> responseEntity = convertToMap(entityContentType.xContent(), response.getEntity().getContent(), false);
        assertNotNull(responseEntity);
        return responseEntity;
    }

    public static Map<String, Object> convertToMap(XContent xContent, String string, boolean ordered) {
        try (XContentParser parser = xContent.createParser(XContentParserConfiguration.EMPTY, string)) {
            return ordered ? parser.mapOrdered() : parser.map();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse content to map", e);
        }
    }

    public static Map<String, Object> convertToMap(XContent xContent, InputStream input, boolean ordered) {
        return convertToMap(xContent, input, ordered, null, null);
    }

    private static Map<String, Object> convertToMap(
        XContent xContent,
        InputStream input,
        boolean ordered,
        @Nullable Set<String> include,
        @Nullable Set<String> exclude
    ) {
        try (
            XContentParser parser = xContent.createParser(XContentParserConfiguration.EMPTY.withFiltering(include, exclude, false), input)
        ) {
            return ordered ? parser.mapOrdered() : parser.map();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse content to map", e);
        }
    }

    /**
     * Return a {@link String} that is the json representation of the provided {@link ToXContent}.
     * Wraps the output into an anonymous object if needed. The content is not pretty-printed
     * nor human readable.
     */
    public static String toString(ToXContent toXContent) {
        return toString(toXContent, false, false);
    }

    /**
     * Return a {@link String} that is the json representation of the provided {@link ToXContent}.
     * Wraps the output into an anonymous object if needed.
     * Allows to configure the params.
     * The content is not pretty-printed nor human readable.
     */
    public static String toString(ToXContent toXContent, ToXContent.Params params) {
        return toString(toXContent, params, false, false);
    }

    /**
     * Returns a string representation of the builder (only applicable for text based xcontent).
     * @param xContentBuilder builder containing an object to converted to a string
     */
    public static String toString(XContentBuilder xContentBuilder) {
        xContentBuilder.close();
        return ((ByteArrayOutputStream) xContentBuilder.getOutputStream()).toString(StandardCharsets.UTF_8);
    }

    /**
     * Return a {@link String} that is the json representation of the provided {@link ToXContent}.
     * Wraps the output into an anonymous object if needed. Allows to control whether the outputted
     * json needs to be pretty printed and human readable.
     *
     */
    public static String toString(ToXContent toXContent, boolean pretty, boolean human) {
        return toString(toXContent, ToXContent.EMPTY_PARAMS, pretty, human);
    }

    /**
     * Return a {@link String} that is the json representation of the provided {@link ToXContent}.
     * Wraps the output into an anonymous object if needed.
     * Allows to configure the params.
     * Allows to control whether the outputted json needs to be pretty printed and human readable.
     */
    private static String toString(ToXContent toXContent, ToXContent.Params params, boolean pretty, boolean human) {
        try {
            XContentBuilder builder = createBuilder(pretty, human);
            if (toXContent.isFragment()) {
                builder.startObject();
            }
            toXContent.toXContent(builder, params);
            if (toXContent.isFragment()) {
                builder.endObject();
            }
            return toString(builder);
        } catch (IOException e) {
            try {
                XContentBuilder builder = createBuilder(pretty, human);
                builder.startObject();
                builder.field("error", "error building toString out of XContent: " + e.getMessage());
                builder.field("stack_trace", ExceptionUtils.readStackTrace(e));
                builder.endObject();
                return toString(builder);
            } catch (IOException e2) {
                throw new RuntimeException("cannot generate error message for deserialization", e);
            }
        }
    }

    private static XContentBuilder createBuilder(boolean pretty, boolean human) throws IOException {
        XContentBuilder builder = JsonXContent.contentBuilder();
        if (pretty) {
            builder.prettyPrint();
        }
        if (human) {
            builder.humanReadable(true);
        }
        return builder;
    }
}
