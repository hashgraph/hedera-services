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

package com.swirlds.base.internal;

import com.swirlds.base.internal.impl.BaseExecutorFactoryImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This factory creates / provides executors for the base modules. The factory should only be used by code in the base
 * modules that highly needs an asynchronous executor. All executors that are created by this factory are daemon
 * threads and have a low priority.
 */
public interface BaseExecutorFactory {

    /**
     * Returns a {@link ScheduledExecutorService} that is based on a single thread.
     * Calling the method several times will always return the same instance.
     * @return the executor
     */
    @NonNull
    ScheduledExecutorService getScheduledExecutor();

    /**
     * Submits a value-returning task for execution and returns a Future representing the pending results of the task.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @see java.util.concurrent.ExecutorService#submit(Runnable)
     */
    @NonNull
    default Future<Void> submit(@NonNull final Runnable task) {
        return getScheduledExecutor().submit(task, null);
    }

    /**
     * Submits a value-returning task for execution and returns a Future representing the pending results of the task.
     *
     * @param task the task to submit
     * @param <V>  the type of the task's result
     * @return a Future representing pending completion of the task
     * @see java.util.concurrent.ExecutorService#submit(Callable)
     */
    @NonNull
    default <V> Future<V> submit(@NonNull final Callable<V> task) {
        return getScheduledExecutor().submit(task);
    }

    /**
     * Creates and executes a periodic action that becomes enabled first after the given initial delay, and subsequently
     * with the given period.
     *
     * @param task         the task to execute
     * @param initialDelay the time to delay first execution
     * @param period       the period between successive executions
     * @param unit         the time unit of the initialDelay and period parameters
     * @return a ScheduledFuture representing pending completion of the series of repeated tasks.
     * @see java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)
     */
    @NonNull
    default ScheduledFuture<?> scheduleAtFixedRate(
            @NonNull final Runnable task, long initialDelay, long period, @NonNull final TimeUnit unit) {
        return getScheduledExecutor().scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    /**
     * Creates and executes a one-shot action that becomes enabled after the given delay.
     *
     * @param command the task to execute
     * @param delay   the time from now to delay execution
     * @param unit    the time unit of the delay parameter
     * @return a ScheduledFuture representing pending completion of the task.
     */
    @NonNull
    default ScheduledFuture<?> schedule(@NonNull final Runnable command, long delay, @NonNull final TimeUnit unit) {
        return getScheduledExecutor().schedule(command, delay, unit);
    }

    /**
     * Returns the singleton instance of this factory.
     *
     * @return the instance
     */
    @NonNull
    static BaseExecutorFactory getInstance() {
        return BaseExecutorFactoryImpl.getInstance();
    }
}
