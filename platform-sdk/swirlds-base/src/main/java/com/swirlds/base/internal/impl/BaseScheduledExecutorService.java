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

import com.swirlds.base.internal.BaseExecutorObserver;
import com.swirlds.base.internal.BaseTask;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
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

    private final List<BaseExecutorObserver> observers;

    private BaseScheduledExecutorService() {
        final ThreadFactory threadFactory = BaseExecutorThreadFactory.getInstance();
        this.innerService = Executors.newScheduledThreadPool(CORE_POOL_SIZE, threadFactory);
        this.observers = new CopyOnWriteArrayList<>();
        final Thread shutdownHook = new Thread(() -> innerService.shutdown());
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

    private void onTaskSubmitted(@NonNull String type, @NonNull String name) {
        observers.forEach(observer -> observer.onTaskSubmitted(type, name));
    }

    private void onTaskStarted(@NonNull final String type, @NonNull final String name) {
        observers.forEach(observer -> observer.onTaskStarted(type, name));
    }

    private void onTaskDone(@NonNull final String type, @NonNull final String name) {
        observers.forEach(observer -> observer.onTaskDone(type, name));
    }

    private void onTaskFailed(@NonNull final String type, @NonNull final String name) {
        observers.forEach(observer -> observer.onTaskFailed(type, name));
    }

    @NonNull
    private String getName(@NonNull final Runnable runnable) {
        if (runnable instanceof BaseTask baseTask) {
            return baseTask.getName();
        }
        return BaseTask.DEFAULT_TYPE_AND_NAME;
    }

    @NonNull
    private String getType(@NonNull final Runnable runnable) {
        if (runnable instanceof BaseTask baseTask) {
            return baseTask.getType();
        }
        return BaseTask.DEFAULT_TYPE_AND_NAME;
    }

    @NonNull
    private <V> String getName(@NonNull final Callable<V> callable) {
        if (callable instanceof BaseTask baseTask) {
            return baseTask.getName();
        }
        return BaseTask.DEFAULT_TYPE_AND_NAME;
    }

    @NonNull
    private <V> String getType(@NonNull final Callable<V> callable) {
        if (callable instanceof BaseTask baseTask) {
            return baseTask.getType();
        }
        return BaseTask.DEFAULT_TYPE_AND_NAME;
    }

    @NonNull
    private Runnable wrapAndCallSubmit(@NonNull final Runnable command) {
        final String name = getType(command);
        final String type = getName(command);
        onTaskSubmitted(type, name);
        return () -> {
            onTaskStarted(type, name);
            try {
                command.run();
                onTaskDone(type, name);
            } catch (Throwable t) {
                onTaskFailed(type, name);
                throw t;
            }
        };
    }

    @NonNull
    private <V> Callable<V> wrapAndCallSubmit(@NonNull final Callable<V> callable) {
        final String name = getType(callable);
        final String type = getName(callable);
        onTaskSubmitted(type, name);
        return () -> {
            onTaskStarted(type, name);
            try {
                final V result = callable.call();
                onTaskDone(type, name);
                return result;
            } catch (Throwable t) {
                onTaskFailed(type, name);
                throw t;
            }
        };
    }

    @Override
    public ScheduledFuture<?> schedule(
            @NonNull final Runnable command, final long delay, @NonNull final TimeUnit unit) {
        final Runnable wrapped = wrapAndCallSubmit(command);
        return innerService.schedule(wrapped, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(
            @NonNull final Callable<V> callable, final long delay, @NonNull final TimeUnit unit) {
        final Callable<V> wrapped = wrapAndCallSubmit(callable);
        return innerService.schedule(wrapped, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
            @NonNull final Runnable command, final long initialDelay, final long period, @NonNull final TimeUnit unit) {
        final Runnable wrapped = wrapAndCallSubmit(command);
        return innerService.scheduleAtFixedRate(wrapped, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
            @NonNull final Runnable command, final long initialDelay, final long delay, @NonNull final TimeUnit unit) {
        final Runnable wrapped = wrapAndCallSubmit(command);
        return innerService.scheduleWithFixedDelay(wrapped, initialDelay, delay, unit);
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
        final Callable<T> wrapped = wrapAndCallSubmit(task);
        return innerService.submit(wrapped);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        final Runnable wrapped = wrapAndCallSubmit(task);
        return innerService.submit(wrapped, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        final Runnable wrapped = wrapAndCallSubmit(task);
        return innerService.submit(wrapped);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        Collection<? extends Callable<T>> wrapped =
                tasks.stream().map(this::wrapAndCallSubmit).toList();
        return innerService.invokeAll(wrapped);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        Collection<? extends Callable<T>> wrapped =
                tasks.stream().map(this::wrapAndCallSubmit).toList();
        return innerService.invokeAll(wrapped, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        Collection<? extends Callable<T>> wrapped =
                tasks.stream().map(this::wrapAndCallSubmit).toList();
        return innerService.invokeAny(wrapped);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        Collection<? extends Callable<T>> wrapped =
                tasks.stream().map(this::wrapAndCallSubmit).toList();
        return innerService.invokeAny(wrapped, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        final Runnable wrapped = wrapAndCallSubmit(command);
        innerService.execute(wrapped);
    }
}
