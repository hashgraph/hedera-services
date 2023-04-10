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

package com.swirlds.common.threading.manager.internal;

import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadPoolConfiguration;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.manager.ThreadBuilder;
import com.swirlds.common.threading.manager.ThreadManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * A simple thread manager. The goal of this implementation is to create threads without complaining about lifecycle.
 * Eventually, this implementation should not be used in production code.
 */
public final class AdHocThreadManager implements ThreadBuilder, ThreadManager {

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Thread buildThread(@NonNull final Runnable runnable) {
        return new Thread(runnable);
    }

    /**
     * Create a thread factory. TODO abstract class?
     */
    @NonNull
    private ThreadFactory createThreadFactory(@NonNull final String threadName) {
        // TODO is this correct?
        return new ThreadConfiguration(this)
                .setThreadName(threadName)
                .buildFactory();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorService createCachedThreadPool(@NonNull final String name) {
        return Executors.newCachedThreadPool(createThreadFactory(name));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorService createSingleThreadExecutor(@NonNull final String name) {
        return Executors.newSingleThreadExecutor(createThreadFactory(name));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorService createFixedThreadPool(
            @NonNull final String name, final int threadCount) {
        return Executors.newFixedThreadPool(threadCount, createThreadFactory(name));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ScheduledExecutorService createSingleThreadScheduledExecutor(@NonNull String name) {
        return Executors.newSingleThreadScheduledExecutor(createThreadFactory(name));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ScheduledExecutorService createScheduledThreadPool(
            @NonNull final String name, final int threadCount) {
        return Executors.newScheduledThreadPool(threadCount, createThreadFactory(name));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ThreadConfiguration newThreadConfiguration() {
        return new ThreadConfiguration(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <T extends InterruptableRunnable> StoppableThreadConfiguration<T> newStoppableThreadConfiguration() {
        return new StoppableThreadConfiguration<>(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <T> QueueThreadConfiguration<T> newQueueThreadConfiguration() {
        return new QueueThreadConfiguration<T>(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <T> QueueThreadPoolConfiguration<T> newQueueThreadPoolConfiguration() {
        return new QueueThreadPoolConfiguration<T>(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public MultiQueueThreadConfiguration newMultiQueueThreadConfiguration() {
        return new MultiQueueThreadConfiguration(this);
    }
}
