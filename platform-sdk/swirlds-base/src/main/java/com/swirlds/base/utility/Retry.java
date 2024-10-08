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

package com.swirlds.base.utility;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Utility class that provides a method to retry a given operation until a specified condition is met or the maximum
 * number of attempts have been exhausted. Methods may support an optional delay between each retry attempt.
 */
public final class Retry {
    /**
     * Private constructor to prevent utility class instantiation.
     **/
    private Retry() {}

    /**
     * Evaluates a given {@code value} using the provided {@code checkFn} method until the return value is {@code true}
     * or the {@code maxAttempts} are exhausted. A delay of {@code delayMs} will be applied between each call to the
     * {@code checkFn} method. Setting the {@code delayMs} parameter to zero (0) will disable the delay mechanism and
     * the {@code checkFn} method will be called as fast as possible.
     *
     * @param value       the user value to be passed to the {@code checkFn} method.
     * @param maxAttempts the maximum number of retry attempts.
     * @param delayMs     the delay between retry attempts. must greater than or equal to zero (0). if a zero value is
     *                    specified then no delay will be applied.
     * @param <T>         the type of the value argument.
     * @return true if the {@code checkFn} method returns true before all attempts have been exhausted; false
     * if the retries are exhausted.
     * @throws NullPointerException     if the {@code checkFn} or the {@code value} arguments are a {@code null} values.
     * @throws IllegalArgumentException if the {@code maxAttempts} argument is less than or equal to zero (0) or the
     *                                  {@code delayMs} argument is less than zero (0).
     * @throws ExecutionException       if an unhandled {@link RuntimeException} was thrown by the {@code checkFn}
     *                                  method call.
     */
    public static <T> boolean check(
            @NonNull Function<T, Boolean> checkFn, @NonNull final T value, final int maxAttempts, final int delayMs)
            throws InterruptedException, ExecutionException {
        Objects.requireNonNull(checkFn, "checkFn must not be null");
        Objects.requireNonNull(value, "value must not be null");

        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("The maximum number of attempts must be greater than zero (0)");
        }

        if (delayMs < 0) {
            throw new IllegalArgumentException("The delay must be greater than or equal to zero (0)");
        }

        try {
            for (int i = 0; i < maxAttempts; i++) {
                if (checkFn.apply(value)) {
                    return true;
                }

                if (delayMs > 0) {
                    TimeUnit.MILLISECONDS.sleep(delayMs);
                }
            }
        } catch (final Throwable e) {
            // If an exception other than UnknownHostException or InterruptedException is thrown, then we should
            // immediately skip any further retry attempts.
            throw new ExecutionException("Unhandled exception thrown during a retryable operation attempt", e);
        }

        // If we failed to resolve the name to an IP address within the maximum retry threshold, then we should fall
        // through to here to fail.
        return false;
    }
}
