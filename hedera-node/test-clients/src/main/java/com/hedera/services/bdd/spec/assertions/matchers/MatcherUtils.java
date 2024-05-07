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

import java.util.List;
import java.util.function.Function;
import org.testcontainers.shaded.org.hamcrest.Matcher;

/**
 * Utility class with methods for creating custom matchers.
 *
 * @author vyanev
 */
public final class MatcherUtils {

    private MatcherUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * @param expected the expected object
     * @param stopClass the class to stop at when comparing fields
     * @return {@link FieldByFieldMatcher} checking if the objects have equal fields
     */
    public static <T> FieldByFieldMatcher<T> withEqualFields(final T expected, final Class<?> stopClass) {
        return new FieldByFieldMatcher<>(expected, stopClass);
    }

    /**
     * @param expectedGas the expected gas
     * @return {@link GasMatcher} checking for equality within a range of 32 units
     */
    public static GasMatcher within64Units(final Long expectedGas) {
        return new GasMatcher(expectedGas, false, 64L);
    }

    /**
     * @param expectedItems list of expected items
     * @param actualItems list of actual items
     * @param matcher the matcher to use for comparison
     * @param <T> the type of the items
     * @return list of items that are in the expected list but not in the actual list
     */
    public static <T> List<T> getMismatchedItems(
            final List<T> expectedItems, final List<T> actualItems, final Function<T, Matcher<T>> matcher) {
        return expectedItems.stream()
                .filter(expectedItem -> actualItems.stream()
                        .noneMatch(actualItem -> matcher.apply(expectedItem).matches(actualItem)))
                .toList();
    }
}
