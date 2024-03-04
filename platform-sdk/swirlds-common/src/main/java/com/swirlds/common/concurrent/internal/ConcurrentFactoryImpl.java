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

import com.swirlds.common.concurrent.ConcurrentFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ThreadFactory;

public class ConcurrentFactoryImpl implements ConcurrentFactory {

    private final ThreadFactory threadFactory;

    private final ForkJoinWorkerThreadFactory factory;

    private final UncaughtExceptionHandler handler;

    public ConcurrentFactoryImpl(
            @NonNull final ThreadFactory threadFactory,
            @NonNull final ForkJoinWorkerThreadFactory factory,
            @NonNull final UncaughtExceptionHandler handler) {
        this.threadFactory = threadFactory;
        this.factory = factory;
        this.handler = handler;
    }

    @Override
    public ForkJoinPool createForkJoinPool(int parallelism) {
        return new ForkJoinPool(parallelism, factory, handler, false);
    }

    @Override
    public ExecutorService createExecutorService(int threadCount) {
        return Executors.newFixedThreadPool(threadCount, threadFactory);
    }

    public static ConcurrentFactoryImpl create(
            final String groupName, Runnable onStartup, UncaughtExceptionHandler exceptionHandler) {
        final ThreadGroup group = new ThreadGroup(groupName);
        final ThreadFactory threadFactory =
                new ThreadFactoryImpl(group, groupName + "Thread", exceptionHandler, onStartup);
        final ForkJoinWorkerThreadFactory forkJoinThreadFactory =
                new ForkJoinWorkerThreadFactoryImpl(group, groupName + "Thread", onStartup);
        return new ConcurrentFactoryImpl(threadFactory, forkJoinThreadFactory, exceptionHandler);
    }
}
