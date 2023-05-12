/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.futures;

/**
 * Utility methods for {@link java.util.concurrent.Future Future} and Future-related objects.
 */
public final class FutureUtils {

    private FutureUtils() {}

    /**
     * Get a value from a future without blocking or throwing execution exceptions. This should only be performed
     * if it is known that the future is done and not cancelled.
     *
     * @return the value
     * @throws IllegalStateException
     * 		if the future is not done or the future is cancelled
     */
    public static <T> T getImmediately(final StandardFuture<T> future) {
        if (!future.isDone() || future.isCancelled()) {
            throw new IllegalStateException("Future does not currently have an available value");
        }
        return future.getRawValue();
    }
}
