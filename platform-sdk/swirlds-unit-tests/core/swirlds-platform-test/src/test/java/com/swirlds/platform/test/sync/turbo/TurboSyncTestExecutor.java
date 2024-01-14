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

package com.swirlds.platform.test.sync.turbo;

import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.threading.utility.ThrowingRunnable;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Callable;

/**
 * Pretends to be a parallel executor, but secretly it just runs both operations on the same thread.
 * Intended to be simple for use during testing.
 */
public class TurboSyncTestExecutor implements ParallelExecutor {
    @Override
    public boolean isImmutable() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throw new UnsupportedOperationException("unused by turbo sync tests");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T doParallel(@NonNull final Callable<T> task1, @NonNull final Callable<Void> task2) {
        throw new UnsupportedOperationException("unused by turbo sync tests");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doParallel(
            @NonNull final ThrowingRunnable task1,
            @NonNull final ThrowingRunnable task2,
            @NonNull final Runnable onThrow)
            throws ParallelExecutionException {
        try {
            // Order is intentional. We want to write first and then read.
            // During turbo sync, task 2 is writing, task 1 is reading.
            task2.call();
            task1.call();
        } catch (final Exception e) {
            throw new ParallelExecutionException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T doParallel(
            @NonNull final Callable<T> task1, @NonNull final Callable<Void> task2, @NonNull final Runnable onThrow) {
        throw new UnsupportedOperationException("unused by turbo sync tests");
    }
}
