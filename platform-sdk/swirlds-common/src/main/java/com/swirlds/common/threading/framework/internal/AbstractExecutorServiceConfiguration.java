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

package com.swirlds.common.threading.framework.internal;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.threading.framework.config.ExecutorServiceProfile;
import com.swirlds.common.threading.manager.ExecutorServiceRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A fluent style builder for executor services.
 */
public class AbstractExecutorServiceConfiguration<T extends AbstractExecutorServiceConfiguration<T>> {

    private static final Logger logger = LogManager.getLogger(AbstractExecutorServiceConfiguration.class);

    /**
     * An default exception handler for executor services.
     */
    private static final Thread.UncaughtExceptionHandler defaultExceptionHandler =
            (final Thread t, final Throwable e) ->
                    logger.error(EXCEPTION.getMarker(), "exception on thread {}", t.getName(), e);

    private final ExecutorServiceRegistry registry;
    private final String name;

    private int corePoolSize = 1;
    private int maximumPoolSize = 1;
    private Duration keepAliveTime = Duration.ofSeconds(0);
    private BlockingQueue<Runnable> queue;

    private int queueSize;
    private UncaughtExceptionHandler uncaughtExceptionHandler = defaultExceptionHandler;

    private ExecutorServiceProfile profile = ExecutorServiceProfile.CUSTOM;

    /**
     * Create a new configuration.
     *
     * @param registry the registry to register the executor service with
     * @param name     the name of the executor service
     */
    public AbstractExecutorServiceConfiguration(
            @NonNull final ExecutorServiceRegistry registry, @NonNull final String name) {
        this.name = Objects.requireNonNull(name);
        this.registry = Objects.requireNonNull(registry);
    }

    /**
     * Builds a thread factory for an executor service.
     *
     * @return a thread factory
     */
    protected @NonNull ThreadFactory buildThreadFactory() {
        final AtomicInteger threadNumber = new AtomicInteger(0);
        return (final Runnable runnable) -> {
            final String fullName = "<" + name + " #" + threadNumber.getAndIncrement() + ">";
            final Thread thread = new Thread(runnable, fullName);
            if (uncaughtExceptionHandler != null) {
                thread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            }
            return thread;
        };
    }

    /**
     * Get the configured profile.
     *
     * @return the configured profile
     */
    @NonNull
    @SuppressWarnings("unchecked")
    protected T setProfile(@NonNull final ExecutorServiceProfile profile) {
        this.profile = profile;
        return (T) this;
    }

    /**
     * Get the configured number of threads.
     *
     * @return the configured number of threads
     */
    protected int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Set the number of threads for the pool. Default 1.
     *
     * @param corePoolSize the number of threads
     * @return this object
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public T setCorePoolSize(final int corePoolSize) {
        this.corePoolSize = corePoolSize;
        return (T) this;
    }

    /**
     * Get the maximum number of threads in this pool.
     *
     * @return the maximum number of threads
     */
    protected int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Set the maximum number of threads for the pool. Default 1.
     *
     * @param maximumPoolSize the number of threads
     * @return this object
     */
    @NonNull
    @SuppressWarnings("unchecked")
    protected T setMaximumPoolSize(final int maximumPoolSize) {
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("Thread count must be at least 1");
        }
        this.maximumPoolSize = maximumPoolSize;
        return (T) this;
    }

    /**
     * Get the keep alive time for this pool.
     */
    protected Duration getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * Set the keep alive time for this pool.
     *
     * @param keepAliveTime the keep alive time
     * @return this object
     */
    @NonNull
    @SuppressWarnings("unchecked")
    protected T setKeepAliveTime(@NonNull final Duration keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
        return (T) this;
    }

    /**
     * Set the maximum queue size for work waiting to be processed. Default unlimited. Ignored if queue implementation
     * is provided via {@link #setQueue(BlockingQueue)}.
     *
     * @param queueSize the maximum queue size, or 0 if there should be no limit.
     * @return this object
     */
    @NonNull
    @SuppressWarnings("unchecked")
    protected T setQueueSize(final int queueSize) {
        if (queueSize < 0) {
            throw new IllegalArgumentException("Queue size must be at least 1 or unlimited");
        }
        this.queueSize = queueSize;
        return (T) this;
    }

    /**
     * Get the configured queue size.
     *
     * @return the configured queue size
     */
    protected int getQueueSize() {
        return queueSize;
    }

    /**
     * Set the uncaught exception handler.
     *
     * @param uncaughtExceptionHandler the uncaught exception handler
     * @return this object
     */
    @NonNull
    @SuppressWarnings("unchecked")
    protected T setUncaughtExceptionHandler(final @NonNull UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        return (T) this;
    }

    /**
     * Set the queue for work waiting to be processed. Default is a {@link java.util.concurrent.LinkedBlockingQueue}.
     *
     * @param queue the queue
     * @return this object
     */
    @NonNull
    @SuppressWarnings("unchecked")
    protected T setQueue(@NonNull final BlockingQueue<Runnable> queue) {
        this.queue = queue;
        return (T) this;
    }

    /**
     * Build the queue for work waiting to be processed.
     *
     * @return the queue
     */
    protected @NonNull BlockingQueue<Runnable> buildQueue() {
        if (queue == null) {
            if (queueSize == 0) {
                queue = new java.util.concurrent.LinkedBlockingQueue<>();
            } else {
                queue = new java.util.concurrent.ArrayBlockingQueue<>(queueSize);
            }
        }
        return queue;
    }

    /**
     * Get the executor service registry.
     */
    protected ExecutorServiceRegistry getRegistry() {
        return registry;
    }

    /**
     * Apply the selected profile.
     */
    protected void applyProfile() {
        switch (profile) {
            case CUSTOM -> {}
            case CACHED_THREAD_POOL -> {
                corePoolSize = 0;
                maximumPoolSize = Integer.MAX_VALUE;
                keepAliveTime = Duration.ofSeconds(60);
            }
            case SINGLE_THREAD_EXECUTOR -> {
                corePoolSize = 1;
                maximumPoolSize = 1;
                keepAliveTime = Duration.ZERO;
            }
            case FIXED_THREAD_POOL -> {
                maximumPoolSize = corePoolSize;
                keepAliveTime = Duration.ZERO;
            }
            default -> throw new IllegalStateException("Unhandled profile: " + profile);
        }
    }
}
