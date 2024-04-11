/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.spec.assertions.matchers;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.shaded.org.hamcrest.Description;
import org.testcontainers.shaded.org.hamcrest.Matcher;
import org.testcontainers.shaded.org.hamcrest.Matchers;
import org.testcontainers.shaded.org.hamcrest.TypeSafeDiagnosingMatcher;

/**
 * Used in assertions to check if all fields of an object match the expected fields.
 * Adds utilities to ignore fields and use custom matchers for fields.
 *
 * @author vyanev
 *
 * @param <T> the type of the object
 */
public class FieldByFieldMatcher<T> extends TypeSafeDiagnosingMatcher<T> {
    private static final Logger log = LogManager.getLogger(FieldByFieldMatcher.class);

    private final T expected;
    private final Class<?> stopClass;
    private final Function<Object, Matcher<Object>> defaultMatcher;
    private final Map<String, Matcher<?>> customMatchers = new HashMap<>();
    private final Set<String> ignoredFields = new HashSet<>();

    public FieldByFieldMatcher(T expected, Class<?> stopClass) {
        this.expected = expected;
        this.defaultMatcher = Matchers::equalTo;
        this.stopClass = stopClass;
    }

    /**
     * @param customMatchers the custom matchers for the fields
     * @return this {@link FieldByFieldMatcher}
     */
    public FieldByFieldMatcher<T> withCustomMatchersForFields(Map<String, Matcher<?>> customMatchers) {
        this.customMatchers.putAll(customMatchers);
        return this;
    }

    /**
     * @param fields the fields to ignore
     * @return this {@link FieldByFieldMatcher}
     */
    public FieldByFieldMatcher<T> ignoringFields(final String... fields) {
        ignoredFields.addAll(Arrays.asList(fields));
        return this;
    }

    /**
     * @param actual the actual object
     * @param mismatch {@link Description} of the mismatch
     * @return {@code true} if the actual object matches the expected object
     */
    @Override
    protected boolean matchesSafely(T actual, Description mismatch) {
        return areEqual(expected, actual, mismatch);
    }

    /**
     * @param description {@link Description} of the expected object
     */
    @Override
    public void describeTo(Description description) {
        description.appendText(expected.toString());
    }

    private boolean areEqual(Object expected, Object actual, Description mismatch) {
        if (expected == actual) {
            return true;
        } else if (expected == null || actual == null) {
            describeMismatch("****** Actual ******: %s%n".formatted(actual), mismatch);
            return false;
        } else if (expected.getClass().isAssignableFrom(actual.getClass())) {
            try {
                final List<PropertyDescriptor> beanProperties = beanGetterProperties(actual, stopClass);
                for (final PropertyDescriptor beanProperty : beanProperties) {
                    final var fieldName = beanProperty.getName();
                    final var expectedValue = beanProperty.getReadMethod().invoke(expected);
                    final var actualValue = beanProperty.getReadMethod().invoke(actual);

                    if (!haveEqualFieldValue(expectedValue, actualValue, fieldName)) {
                        describeMismatch(
                                """
                                    ****** Mismatch in field '%s' ******
                                    ****** Expected ***: %s
                                    ****** Actual *****: %s
                                    """
                                        .formatted(fieldName, expectedValue, actualValue),
                                mismatch);
                        return false;
                    }
                }
                return true;
            } catch (IntrospectionException | InvocationTargetException | IllegalAccessException e) {
                log.error(e.getMessage(), e);
                return false;
            }
        } else {
            if (!defaultMatcher.apply(expected).matches(actual)) {
                describeMismatch("****** Actual ******: %s%n".formatted(actual), mismatch);
                return false;
            }
            return true;
        }
    }

    private boolean haveEqualFieldValue(Object expected, Object actual, String fieldName) {
        try {
            if (ignoredFields.contains(fieldName)) {
                return true;
            } else if (customMatchers.containsKey(fieldName)) {
                return customMatchers.get(fieldName).matches(actual);
            } else {
                return defaultMatcher.apply(expected).matches(actual);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    private void describeMismatch(String message, Description mismatch) {
        mismatch.appendText(message).appendText("\n");
        log.trace(message);
    }

    private static List<PropertyDescriptor> beanGetterProperties(Object bean, Class<?> stopClass)
            throws IntrospectionException {
        return Arrays.stream(
                        Introspector.getBeanInfo(bean.getClass(), stopClass).getPropertyDescriptors())
                .filter(propertyDescriptor -> Objects.nonNull(propertyDescriptor.getReadMethod()))
                .toList();
    }
}
