/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.manager;

import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadPoolConfiguration;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Responsible for managing threading resources.
 */
public interface ThreadManager {

    /**
     * Create a new cached thread pool. If this thread manager has not yet been started, work submitted the executor
     * service will be not be handled until after the thread manager has been started (with the exception of the
     * invokeAny() method, which will throw if called prior to the thread manager being started).
     *
     * @param name the name of the thread pool
     * @return a new cached thread pool
     */
    @NonNull
    ExecutorService createCachedThreadPool(@NonNull final String name);

    /**
     * Create a new single thread executor. If this thread manager has not yet been started, work submitted the executor
     * service will be not be handled until after the thread manager has been started (with the exception of the
     * invokeAny() method, which will throw if called prior to the thread manager being started).
     *
     * @param name the name of the thread pool
     * @return a new single thread executor
     */
    @NonNull
    ExecutorService createSingleThreadExecutor(@NonNull final String name);

    /**
     * Create a new fixed thread pool. If this thread manager has not yet been started, work submitted the executor
     * service will be not be handled until after the thread manager has been started (with the exception of the
     * invokeAny() method, which will throw if called prior to the thread manager being started).
     *
     * @param name        the name of the thread pool
     * @param threadCount the number of threads in the pool
     * @return a new fixed thread pool
     */
    @NonNull
    ExecutorService createFixedThreadPool(@NonNull final String name, final int threadCount);

    /**
     * Create a new single thread scheduled executor. If this thread manager has not yet been started, work submitted
     * the executor service will be not be handled until after the thread manager has been started (with the exception
     * of the invokeAny() method, which will throw if called prior to the thread manager being started).
     *
     * @param name the name of the thread pool
     * @return a new single thread scheduled executor
     */
    @NonNull
    ScheduledExecutorService createSingleThreadScheduledExecutor(@NonNull final String name);

    /**
     * Create a new scheduled thread pool. If this thread manager has not yet been started, work submitted the executor
     * service will be not be handled until after the thread manager has been started (with the exception of the
     * invokeAny() method, which will throw if called prior to the thread manager being started).
     *
     * @param name        the name of the thread pool
     * @param threadCount the number of threads in the pool
     * @return a new scheduled thread pool
     */
    @NonNull
    ScheduledExecutorService createScheduledThreadPool(@NonNull final String name, final int threadCount);

    /**
     * Create a new thread configuration.
     *
     * @return a new thread configuration
     */
    @NonNull
    ThreadConfiguration newThreadConfiguration();

    /**
     * Create a new stoppable thread configuration.
     *
     * @param <T> the type of the runnable
     * @return a new stoppable thread configuration
     */
    @NonNull
    <T extends InterruptableRunnable> StoppableThreadConfiguration<T> newStoppableThreadConfiguration();

    /**
     * Create a new queue thread configuration.
     *
     * @param <T> the type the object in the queue
     * @return a new queue thread configuration
     */
    @NonNull
    <T> QueueThreadConfiguration<T> newQueueThreadConfiguration();

    /**
     * Create a new queue thread configuration.
     *
     * @param clazz provides a generics type hint to the compiler
     * @param <T>   the type the object in the queue
     * @return a new queue thread configuration
     */
    @NonNull
    default <T> QueueThreadConfiguration<T> newQueueThreadConfiguration(@NonNull final Class<T> clazz) {
        return newQueueThreadConfiguration();
    }

    /**
     * Create a new queue thread pool configuration.
     *
     * @param <T> the type the object in the queue
     * @return a new queue thread pool configuration
     */
    @NonNull
    <T> QueueThreadPoolConfiguration<T> newQueueThreadPoolConfiguration();

    /**
     * Create a new queue thread pool configuration.
     *
     * @param clazz provides a generics type hint to the compiler
     * @param <T>   the type the object in the queue
     * @return a new queue thread pool configuration
     */
    @NonNull
    default <T> QueueThreadPoolConfiguration<T> newQueueThreadPoolConfiguration(@NonNull final Class<T> clazz) {
        return newQueueThreadPoolConfiguration();
    }

    /**
     * Create a new multi queue thread configuration.
     *
     * @return a new multi queue thread configuration
     */
    @NonNull
    MultiQueueThreadConfiguration newMultiQueueThreadConfiguration();
}
