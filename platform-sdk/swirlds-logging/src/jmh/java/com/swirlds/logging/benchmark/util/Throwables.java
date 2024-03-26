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

package com.swirlds.logging.benchmark.util;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Convenience methods for creating exceptions with stacktrace as big as requested
 */
public class Throwables {
    public static final Throwable THROWABLE = createThrowable();

    public static final Throwable DEEP_THROWABLE = createThrowableWithDeepCause(20, 20);

    private Throwables() {}

    /**
     * Creates a throwable with a {@code myDepth} stacktrace call and with cause having {@code causeDepth} nested
     * exceptions.
     */
    public static @NonNull Throwable createThrowableWithDeepCause(final int myDepth, final int causeDepth) {
        if (myDepth > 0) {
            return createThrowableWithDeepCause(myDepth - 1, causeDepth);
        }
        try {
            throw createDeepThrowable(causeDepth);
        } catch (Throwable t) {
            return new RuntimeException("test", t);
        }
    }

    /**
     * Creates a throwable with cause having {@code depth} nested exceptions.
     */
    public static @NonNull Throwable createDeepThrowable(final int depth) {
        if (depth <= 0) {
            return new RuntimeException("test");
        }
        return createDeepThrowable(depth - 1);
    }

    /**
     * Creates a throwable.
     */
    public static @NonNull Throwable createThrowable() {
        return new RuntimeException("test");
    }
}
