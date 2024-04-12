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
    public static <T> FieldByFieldMatcher<T> withEqualFields(T expected, Class<?> stopClass) {
        return new FieldByFieldMatcher<>(expected, stopClass);
    }

    /**
     * @param expectedGas the expected gas
     * @return {@link GasMatcher} checking if the gas is within 32 units of the expected gas
     */
    public static GasMatcher within32Units(Long expectedGas) {
        return new GasMatcher(expectedGas, false);
    }
}
