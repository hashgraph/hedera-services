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

package com.hedera.node.app.tss.cryptography.utils;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 *  Some utility functions to perform validations
 */
public class ValidationUtils {

    /**
     * private constructor to ensure static access
     */
    private ValidationUtils() {
        // private constructor to ensure static access
    }

    /**
     * Returns a cast of the parameter to the expected subclass, or throws an exception if not compatible.
     * @param expectedSubclass the expected subclass
     * @param parameter the object to perform the cast to
     * @param <T> the class we expect to receive
     * @param <Y> the superclass we are down casting
     * @return the same instance cast to the expected subtype
     * @throws IllegalArgumentException if T cannot be cast to Y
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public static <T extends Y, Y> T expectOrThrow(
            final @NonNull Class<T> expectedSubclass, final @NonNull Y parameter) {
        Objects.requireNonNull(expectedSubclass, "expectedSubclass must not be null");
        Objects.requireNonNull(parameter, "parameter must not be null");
        if (!expectedSubclass.isAssignableFrom(parameter.getClass())) {
            throw new IllegalArgumentException("Not the element");
        }
        return (T) parameter;
    }

    /**
     * Validates the size of a byte array.
     * @param data the byte array to validate.
     * @param expectedSize the expected size of the array.
     * @param message the error message to throw.
     */
    public static void validateSize(
            @Nullable final byte[] data, final int expectedSize, @NonNull final String message) {
        if (Objects.requireNonNull(data, "data must not be null").length != expectedSize) {
            throw new IllegalArgumentException(message);
        }
    }
}
