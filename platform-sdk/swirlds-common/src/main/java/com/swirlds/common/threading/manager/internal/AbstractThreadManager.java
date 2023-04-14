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

package com.swirlds.common.threading.manager.internal;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.threading.manager.ThreadManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Boilerplate code for thread managers. TODO delete this class
 */
public abstract class AbstractThreadManager implements ThreadManager {

    private static final Logger logger = LogManager.getLogger(AbstractThreadManager.class);

    /**
     * An default exception handler for executor services.
     */
    private static final Thread.UncaughtExceptionHandler defaultExceptionHandler =
            (final Thread t, final Throwable e) ->
                    logger.error(EXCEPTION.getMarker(), "exception on thread {}", t.getName(), e);

    /**
     * Builds a thread factory for an executor service.
     *
     * @param baseName                 the name of the executor service
     * @param uncaughtExceptionHandler use this uncaught exception handler if not null
     * @return a thread factory
     */
    protected @NonNull ThreadFactory buildThreadFactory(
            @NonNull final String baseName, @Nullable final UncaughtExceptionHandler uncaughtExceptionHandler) {
        final AtomicInteger threadNumber = new AtomicInteger(0);
        return (final Runnable runnable) -> {
            final String name = "<" + baseName + " #" + threadNumber.getAndIncrement() + ">";
            final Thread thread = new Thread(runnable, name);
            if (uncaughtExceptionHandler != null) {
                thread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            }
            return thread;
        };
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorService createFixedThreadPool(@NonNull final String name, final int threadCount) {
        Objects.requireNonNull(name);
        return createFixedThreadPool(name, threadCount, defaultExceptionHandler);
    }
}
