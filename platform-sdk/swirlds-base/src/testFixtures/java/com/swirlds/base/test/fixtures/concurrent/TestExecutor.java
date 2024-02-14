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

package com.swirlds.base.test.fixtures.concurrent;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

/**
 * A test executor that can be used to run tasks in parallel. The executor is responsible for managing the threads that
 * are used to run the tasks. The executor is also responsible for waiting for the tasks to complete. All threads that
 * are used are daemon threads. All blocking methods calls will block for a maximum duration that is defined when
 * creating the executor.
 */
public interface TestExecutor {

    /**
     * Executes the given runnables in parallel. This method will block until all the callables have completed. If the
     * callables do not return within the maximum wait time, then the callables will be cancelled and a
     * {@link TimeoutException} will be thrown.
     *
     * @param runnables the runnables to execute
     */
    void executeAndWait(@NonNull Collection<Runnable> runnables);

    /**
     * Executes the given runnables in parallel. This method will block until all the callables have completed. If the
     * callables do not return within the maximum wait time, then the callables will be cancelled and a
     * {@link TimeoutException} will be thrown.
     *
     * @param runnables the runnables to execute
     */
    void executeAndWait(@NonNull Runnable... runnables);

    /**
     * Executes the given callables in parallel. This method will block until all the callables have completed. If the
     * callables do not return within the maximum wait time, then the callables will be cancelled and a
     * {@link TimeoutException} will be thrown.
     *
     * @param callables the callables to execute
     * @param <V>       the type of the callable result
     * @return the results of the callables
     */
    <V> @NonNull List<V> submitAndWait(Collection<Callable<V>> callables);

    /**
     * Executes the given callable in parallel. This method will block until all the callable have completed. If the
     * callable do not return within the maximum wait time, then the callable will be cancelled and a
     * {@link TimeoutException} will be thrown.
     *
     * @param callable a callable array to execute
     * @param <V>      the type of the callable result
     * @return the result of the callable
     */
    <V> @Nullable List<V> submitAndWait(@NonNull Callable<V>... callable);
}
