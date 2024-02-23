/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.pool;

import com.swirlds.base.state.MutabilityException;
import com.swirlds.base.state.Mutable;
import com.swirlds.base.state.Startable;
import com.swirlds.common.threading.utility.ThrowingRunnable;
import java.util.concurrent.Callable;

/**
 * Used for executing tasks in parallel
 */
public interface ParallelExecutor extends Mutable, Startable {
    /**
     * Run two tasks in parallel
     *
     * @param task1 a task to execute in parallel
     * @param task2 a task to execute in parallel
     * @throws MutabilityException        if executed prior to object being started
     * @throws ParallelExecutionException if anything goes wrong
     */
    <T> T doParallel(Callable<T> task1, Callable<Void> task2) throws ParallelExecutionException;

    /**
     * Same as {@link #doParallel(Callable, Callable, Runnable)} but without a return type
     */
    default void doParallel(final ThrowingRunnable task1, final ThrowingRunnable task2, final Runnable onThrow)
            throws ParallelExecutionException {
        doParallel(task1, (Callable<Void>) task2, onThrow);
    }

    /**
     * Run two tasks in parallel
     *
     * @param task1
     * 		a task to execute in parallel
     * @param task2
     * 		a task to execute in parallel
     * @param onThrow
     * 		a task to run if an exception gets thrown
     * @throws MutabilityException
     * 		if executed prior to object being started
     * @throws ParallelExecutionException
     * 		if anything goes wrong
     */
    default <T> T doParallel(final Callable<T> task1, final Callable<Void> task2, final Runnable onThrow)
            throws ParallelExecutionException {
        throw new UnsupportedOperationException("not implemented");
    }
}
