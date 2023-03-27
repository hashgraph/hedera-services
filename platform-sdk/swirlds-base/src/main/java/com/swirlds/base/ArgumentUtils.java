/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

/**
 * Class that contains common checks like null checks as static methods.
 */
public final class ArgumentUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private ArgumentUtils() throws IllegalAccessException {
        throw new IllegalAccessException("Cannot instantiate utility class!");
    }

    /**
     * Throw an {@link NullPointerException} if the supplied argument is {@code null}.
     *
     * @param argument     the argument to check
     * @param argumentName the name of the argument
     */
    public static <T> T throwArgNull(final T argument, final String argumentName) throws NullPointerException {
        if (argument == null) {
            throw new NullPointerException("The supplied argument '%s' cannot be null!".formatted(argumentName));
        }
        return argument;
    }

    /**
     * Throw an {@link IllegalArgumentException} if the supplied {@code String} is blank. Throw an {@link
     * NullPointerException} if the supplied {@code String} is null (see {@link #throwArgNull(Object, String)}).
     *
     * @param arg     the argument checked
     * @param argName the name of the argument
     * @see String#isBlank()
     */
    public static String throwArgBlank(final String arg, final String argName)
            throws NullPointerException, IllegalArgumentException {
        throwArgNull(arg, argName);
        if (arg.isBlank()) {
            throw new IllegalArgumentException("The supplied argument '%s' cannot be blank!".formatted(argName));
        }
        return arg;
    }
}
