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

package com.swirlds.common.threading.framework.config;

import com.swirlds.common.threading.framework.internal.AbstractExecutorServiceConfiguration;
import com.swirlds.common.threading.manager.ExecutorServiceRegistry;
import com.swirlds.common.threading.manager.internal.ManagedExecutorService;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Configures and builds an executor service. Executor service is tied to the lifecycle of the thread manager. If work
 * is submitted to any of the following methods prior to the start of the thread manager, then that work is buffered.
 * The exception to this rule are the invokeAll() and invokeAny() methods, which will throw if called prior to the
 * thread manager being started.
 */
public class ExecutorServiceConfiguration extends AbstractExecutorServiceConfiguration<ExecutorServiceConfiguration> {

    /**
     * Create a new executor service configuration.
     *
     * @param registry the executor service registry
     * @param name     the name of the executor service
     */
    public ExecutorServiceConfiguration(final ExecutorServiceRegistry registry, final String name) {
        super(registry, name);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorServiceConfiguration setUncaughtExceptionHandler(
            @NonNull UncaughtExceptionHandler uncaughtExceptionHandler) {
        return super.setUncaughtExceptionHandler(uncaughtExceptionHandler);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorServiceConfiguration setMaximumPoolSize(final int maximumPoolSize) {
        return super.setMaximumPoolSize(maximumPoolSize);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorServiceConfiguration setKeepAliveTime(@NonNull final Duration keepAliveTime) {
        return super.setKeepAliveTime(keepAliveTime);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorServiceConfiguration setQueueSize(final int queueSize) {
        return super.setQueueSize(queueSize);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorServiceConfiguration setQueue(@NonNull BlockingQueue<Runnable> queue) {
        return super.setQueue(queue);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorServiceConfiguration setProfile(@NonNull ExecutorServiceProfile profile) {
        return super.setProfile(profile);
    }

    /**
     * Build a cached thread pool executor service.
     *
     * @return a new executor service
     */
    @NonNull
    public ExecutorService build() {
        applyProfile();
        final ExecutorService baseExecutor = new ThreadPoolExecutor(
                getCorePoolSize(),
                getMaximumPoolSize(),
                getKeepAliveTime().toMillis(),
                TimeUnit.MILLISECONDS,
                buildQueue(),
                buildThreadFactory());

        final ManagedExecutorService service = new ManagedExecutorService(baseExecutor);

        getRegistry().registerExecutorService(service);
        return service;
    }
}
