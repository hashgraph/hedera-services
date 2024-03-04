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

public class ThreadFactoryImpl implements ThreadFactory {

    private final ThreadGroup group;

    private final String threadNamePrefix;

    private final AtomicLong threadNumber;

    private final Runnable onStartup;

    private final UncaughtExceptionHandler exceptionHandler;

    public ThreadFactoryImpl(
            @NonNull final ThreadGroup group,
            @NonNull final String name,
            @NonNull final UncaughtExceptionHandler exceptionHandler) {
        this(group, name, exceptionHandler, null);
    }

    public ThreadFactoryImpl(
            @NonNull final ThreadGroup group,
            @NonNull final String threadNamePrefix,
            @NonNull final UncaughtExceptionHandler exceptionHandler,
            @Nullable final Runnable onStartup) {
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.threadNamePrefix = Objects.requireNonNull(threadNamePrefix, "name must not be null");
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler must not be null");
        this.onStartup = onStartup;
        this.threadNumber = new AtomicLong(1);
    }

    @Override
    public Thread newThread(Runnable r) {
        final Runnable runnable = () -> {
            if (onStartup != null) {
                onStartup.run();
            }
            r.run();
        };
        Thread thread = new Thread(group, onStartup, threadNamePrefix + "-" + threadNumber.getAndIncrement(), 0);
        thread.setUncaughtExceptionHandler(exceptionHandler);
        return thread;
    }
}
