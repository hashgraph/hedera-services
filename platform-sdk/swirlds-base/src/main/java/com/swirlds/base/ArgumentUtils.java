/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.base;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Class that contains common checks like null checks as static methods.
 */
public final class ArgumentUtils {

    public static final String ERROR_ARGUMENT_NULL = "The supplied argument '%s' cannot be null!";
    public static final String ERROR_ARGUMENT_BLANK = "The supplied argument '%s' cannot be blank!";

    /**
     * Private constructor to prevent instantiation.
     */
    private ArgumentUtils() throws IllegalAccessException {
        throw new IllegalAccessException("Cannot instantiate utility class!");
    }

    /**
     * Throw an {@link IllegalArgumentException} if the supplied {@code String} is blank. Throw an
     * {@link NullPointerException} if the supplied {@code String} is {@code null}.
     *
     * @param argument     the argument checked
     * @param argumentName the name of the argument
     * @see String#isBlank()
     */
    @NonNull
    public static String throwArgBlank(@NonNull final String argument, @NonNull final String argumentName)
            throws NullPointerException, IllegalArgumentException {
        if (argument == null) {
            throw new NullPointerException(ERROR_ARGUMENT_NULL.formatted(argumentName));
        }
        if (argument.isBlank()) {
            throw new IllegalArgumentException(ERROR_ARGUMENT_BLANK.formatted(argumentName));
        }
        return argument;
    }
}
