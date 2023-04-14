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

package com.hedera.node.app.spi.fixtures;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;

/** A convenience class for testing with Hedera specific assertions */
public final class Assertions {
    /**
     * Asserts that the given {@code runnable}, when run, throws a {@link PreCheckException} with
     * the given expected {@link ResponseCodeEnum}.
     *
     * @param runnable The runnable which will throw a {@link PreCheckException}.
     * @param expected The expected status code of the exception
     */
    public static void assertThrowsPreCheck(
            @NonNull final PreCheckRunnable runnable, @NonNull final ResponseCodeEnum expected) {
        try {
            runnable.run();
            throw new AssertionError("Expected " + expected + " but no exception was thrown");
        } catch (final PreCheckException actual) {
            if (!actual.responseCode().equals(expected)) {
                throw new AssertionError("Expected " + expected + " but got " + actual);
            }
        }
    }

    /** A {@link Runnable} like interface that throws the checked {@link PreCheckException}. */
    @FunctionalInterface
    public interface PreCheckRunnable {
        void run() throws PreCheckException;
    }
}
