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

package com.swirlds.logging.benchmark;

import java.util.UUID;

public class LogLikeHellUtils {
    public static final Throwable THROWABLE = createThrowable();

    public static final Throwable DEEP_THROWABLE = createThrowableWithDeepCause(20, 20);

    public static final String USER_1 = UUID.randomUUID().toString();
    public static final String USER_2 = UUID.randomUUID().toString();
    public static final String USER_3 = UUID.randomUUID().toString();

    public static Throwable createThrowableWithCause() {
        try {
            throw createThrowable();
        } catch (Throwable t) {
            return new RuntimeException("test", t);
        }
    }

    public static Throwable createThrowableWithDeepCause(int myDepth, int causeDepth) {
        if (myDepth > 0) {
            return createThrowableWithDeepCause(myDepth - 1, causeDepth);
        }
        try {
            throw createDeepThrowable(causeDepth);
        } catch (Throwable t) {
            return new RuntimeException("test", t);
        }
    }

    public static Throwable createThrowableWithDeepCause(int depth) {
        try {
            throw createDeepThrowable(depth);
        } catch (Throwable t) {
            return new RuntimeException("test", t);
        }
    }

    public static Throwable createDeepThrowable(int depth) {
        if (depth <= 0) {
            return new RuntimeException("test");
        }
        return createDeepThrowable(depth - 1);
    }

    public static Throwable createThrowable() {
        return new RuntimeException("test");
    }
}
