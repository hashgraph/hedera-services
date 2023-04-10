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
import com.swirlds.common.threading.manager.StartableThreadManager;
import com.swirlds.common.threading.manager.ThreadBuilder;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.LifecyclePhase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * A standard implementation of a {@link ThreadManager}. Will not permit work to be done on threads until started.
 */
public final class StandardThreadManager implements StartableThreadManager, ThreadBuilder {

    private LifecyclePhase phase = LifecyclePhase.NOT_STARTED;

    private final Runnable throwIfInWrongPhase = () -> this.throwIfNotInPhase(LifecyclePhase.STARTED);

    /**
     * {@inheritDoc}
     */
    @Override
    public LifecyclePhase getLifecyclePhase() {
        return phase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfNotInPhase(LifecyclePhase.NOT_STARTED);
        phase = LifecyclePhase.STARTED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        phase = LifecyclePhase.STOPPED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Thread buildThread(@NonNull final Runnable runnable) {
        throwIfAfterPhase(LifecyclePhase.STARTED);
        return new ManagedThread(runnable, throwIfInWrongPhase);
    }

    /**
     * Create a thread factory. TODO abstract class?
     */
    @NonNull
    private ThreadFactory createThreadFactory(
            @NonNull final String threadName) { // TODO perhaps we should deprecate this
        return new ThreadConfiguration(this).setThreadName(threadName).buildFactory();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorService createCachedThreadPool(@NonNull final String name) {
        throwIfAfterPhase(LifecyclePhase.STARTED);
        return new ManagedExecutorService(
                Executors.newCachedThreadPool(createThreadFactory(name)), throwIfInWrongPhase);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorService createSingleThreadExecutor(@NonNull final String name) {
        throwIfAfterPhase(LifecyclePhase.STARTED);
        return new ManagedExecutorService(
                Executors.newSingleThreadExecutor(createThreadFactory(name)), throwIfInWrongPhase);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorService createFixedThreadPool(@NonNull final String name, int threadCount) {
        throwIfAfterPhase(LifecyclePhase.STARTED);
        return new ManagedExecutorService(
                Executors.newFixedThreadPool(threadCount, createThreadFactory(name)), throwIfInWrongPhase);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ScheduledExecutorService createSingleThreadScheduledExecutor(final @NonNull String name) {
        throwIfAfterPhase(LifecyclePhase.STARTED);
        return new ManagedScheduledExecutorService(
                Executors.newSingleThreadScheduledExecutor(createThreadFactory(name)), throwIfInWrongPhase);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ScheduledExecutorService createScheduledThreadPool(@NonNull final String name, int threadCount) {
        throwIfAfterPhase(LifecyclePhase.STARTED);
        return new ManagedScheduledExecutorService(
                Executors.newScheduledThreadPool(threadCount, createThreadFactory(name)), throwIfInWrongPhase);
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
