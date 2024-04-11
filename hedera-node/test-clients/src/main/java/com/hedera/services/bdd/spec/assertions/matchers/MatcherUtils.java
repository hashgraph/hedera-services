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

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.testcontainers.shaded.org.hamcrest.FeatureMatcher;
import org.testcontainers.shaded.org.hamcrest.Matcher;
import org.testcontainers.shaded.org.hamcrest.Matchers;
import org.testcontainers.shaded.org.hamcrest.collection.IsIterableContainingInAnyOrder;

/**
 * Utility class with methods for creating custom matchers.
 *
 * @author vyanev
 */
@SuppressWarnings("java:S1452") // wildcard types
public class MatcherUtils {

    private MatcherUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Checks if the actual items contain the expected items.
     *
     * @param items the items to match
     * @return {@link IsIterableContainingInAnyOrder} that checks if the actual items contain the expected items
     */
    public static <T> IsIterableContainingInAnyOrder<T> containsInAnyOrder(Collection<T> items) {
        return containsInAnyOrder(items, Matchers::equalTo);
    }

    /**
     * Maps each item to a {@link Matcher} and then checks if the actual items contain the expected items.
     *
     * @param items the items to match
     * @param mapper the function to map each item to a {@link Matcher}
     * @return {@link IsIterableContainingInAnyOrder} that checks if the actual items contain the expected items
     */
    public static <T> IsIterableContainingInAnyOrder<T> containsInAnyOrder(
            Collection<T> items, Function<T, Matcher<? super T>> mapper) {
        final var itemMatchers = items.stream().map(mapper).collect(Collectors.toCollection(ArrayList::new));

        return new IsIterableContainingInAnyOrder<>(itemMatchers);
    }

    /**
     * @param expected the expected object
     * @param stopClass the class to stop at when comparing fields
     * @return {@link FieldByFieldMatcher} that checks if the actual object has equal fields to the expected object
     */
    public static <T> FieldByFieldMatcher<T> withEqualFields(T expected, Class<?> stopClass) {
        return new FieldByFieldMatcher<>(expected, stopClass);
    }

    /**
     * Applies a mapper function to the actual object and then matches the result with the expected value.
     *
     * @param mapper  the mapper function
     * @param matcher the matcher
     */
    public static <T, E> Matcher<T> matchesProperty(Function<T, E> mapper, Matcher<E> matcher) {
        return new FeatureMatcher<>(matcher, matcher.toString(), mapper.toString()) {
            public E featureValueOf(T actual) {
                return mapper.apply(actual);
            }
        };
    }

    /**
     * Checks if the actual gas is within 32 units of the expected gas.
     *
     * @param expectedGas the expected gas
     * @return {@link GasMatcher} that checks if the actual gas is within 32 units of the expected gas
     */
    public static GasMatcher within32Units(Long expectedGas) {
        return new GasMatcher(expectedGas, false);
    }
}
