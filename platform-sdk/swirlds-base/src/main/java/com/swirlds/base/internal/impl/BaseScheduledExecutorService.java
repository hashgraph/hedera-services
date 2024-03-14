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

package com.swirlds.base.internal.impl;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A scheduled executor service for the base modules. The executor is based on a single thread and is a daemon thread
 * with a low priority.
 * <p>
 * This class is a singleton and should only be used by code in the base modules that highly needs an asynchronous
 * executor.
 */
public class BaseScheduledExecutorService implements ScheduledExecutorService {

    public static final int CORE_POOL_SIZE = 1;

    private static volatile BaseScheduledExecutorService instance;

    private static final Lock instanceLock = new ReentrantLock();

    private final ScheduledExecutorService innerService;

    private BaseScheduledExecutorService() {
        final ThreadFactory threadFactory = BaseExecutorThreadFactory.getInstance();
        this.innerService = Executors.newScheduledThreadPool(CORE_POOL_SIZE, threadFactory);
        Thread shutdownHook = new Thread(() -> innerService.shutdown());
        shutdownHook.setName("BaseScheduledExecutorService-shutdownHook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * Returns the singleton instance of this executor.
     *
     * @return the instance
     */
    public static BaseScheduledExecutorService getInstance() {
        if (instance == null) {
            instanceLock.lock();
            try {
                if (instance == null) {
                    instance = new BaseScheduledExecutorService();
                }
            } finally {
                instanceLock.unlock();
            }
        }
        return instance;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return innerService.schedule(command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return innerService.schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return innerService.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return innerService.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    public void shutdown() {
        throw new IllegalStateException("This executor is managed by the base modules and should not be shut down");
    }

    @Override
    public List<Runnable> shutdownNow() {
        throw new IllegalStateException("This executor is managed by the base modules and should not be shut down");
    }

    @Override
    public boolean isShutdown() {
        return innerService.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return innerService.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return innerService.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return innerService.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return innerService.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return innerService.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return innerService.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return innerService.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return innerService.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return innerService.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        innerService.execute(command);
    }
}
