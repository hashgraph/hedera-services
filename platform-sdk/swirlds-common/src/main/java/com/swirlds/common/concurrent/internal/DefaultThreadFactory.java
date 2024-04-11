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

package com.swirlds.common.concurrent.internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * A default implementation of {@link ThreadFactory}.
 */
public class DefaultThreadFactory implements ThreadFactory {

    /**
     * The thread group.
     */
    private final ThreadGroup group;

    /**
     * The thread name factory.
     */
    private final Supplier<String> threadNameFactory;

    /**
     * The runnable to run on startup.
     */
    private final Runnable onStartup;

    /**
     * The uncaught exception handler for threads.
     */
    private final UncaughtExceptionHandler exceptionHandler;

    /**
     * Create a new instance of {@link DefaultThreadFactory}.
     *
     * @param group             the thread group
     * @param threadNameFactory the thread name factory
     * @param exceptionHandler  the uncaught exception handler
     * @param onStartup         the runnable to run on startup
     */
    public DefaultThreadFactory(
            @NonNull final ThreadGroup group,
            @NonNull final Supplier<String> threadNameFactory,
            @NonNull final UncaughtExceptionHandler exceptionHandler,
            @Nullable final Runnable onStartup) {
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.threadNameFactory = Objects.requireNonNull(threadNameFactory, "name must not be null");
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler must not be null");
        this.onStartup = onStartup;
    }

    @Override
    @NonNull
    public Thread newThread(@NonNull final Runnable innerRunnable) {
        Objects.requireNonNull(innerRunnable, "innerRunnable must not be null");
        final Runnable runnable = () -> {
            if (onStartup != null) {
                onStartup.run();
            }
            innerRunnable.run();
        };
        Thread thread = new Thread(group, runnable, threadNameFactory.get(), 0);
        thread.setUncaughtExceptionHandler(exceptionHandler);
        return thread;
    }

    /**
     * Create a new instance of {@link DefaultThreadFactory}.
     * @param threadNamePrefix the thread name prefix
     * @return the new instance
     */
    @NonNull
    public static Supplier<String> createThreadNameFactory(@NonNull final String threadNamePrefix) {
        Objects.requireNonNull(threadNamePrefix, "threadNamePrefix must not be null");
        final AtomicLong threadNumber = new AtomicLong(1);
        return () -> threadNamePrefix + "-" + threadNumber.getAndIncrement();
    }
}
