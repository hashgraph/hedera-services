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

package com.swirlds.common.threading.pool;

import com.swirlds.common.threading.manager.ThreadManager;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * An implementation that uses a CachedThreadPool to execute parallel tasks
 */
public class CachedPoolParallelExecutor implements ParallelExecutor {
    private static final Runnable NOOP = () -> {};

    /**
     * The thread pool used by this class.
     */
    private final ExecutorService threadPool;

    private boolean immutable = false;

    /**
     * @param threadManager
     * 		responsible for managing thread lifecycles
     * @param name
     * 		the name given to the threads in the pool
     */
    public CachedPoolParallelExecutor(final ThreadManager threadManager, final String name) {
        threadPool = threadManager.createCachedThreadPool("parallel-executor: " + name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isImmutable() {
        return immutable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfImmutable("should only be started once");
        immutable = true;
    }

    /**
     * Run two tasks in parallel, the first one in the current thread, and the second in a background thread.
     *
     * This method returns only after both have finished.
     *
     * @param foregroundTask
     * 		a task to execute in parallel
     * @param backgroundTask
     * 		a task to execute in parallel
     * @param onThrow
     * 		a cleanup task to be executed if an exception gets thrown. if the foreground task throws an exception, this
     * 		could be used to stop the background task, but not vice versa
     * @throws ParallelExecutionException
     * 		if either of the invoked tasks throws an exception. if both throw an exception, then the foregroundTask
     * 		exception will be the cause and the backgroundTask exception will be the suppressed exception
     */
    @Override
    public <T> T doParallel(
            final Callable<T> foregroundTask, final Callable<Void> backgroundTask, final Runnable onThrow)
            throws ParallelExecutionException {
        throwIfMutable("must be started first");

        final Future<Void> future = threadPool.submit(backgroundTask);

        // exception to throw, if any of the tasks throw
        ParallelExecutionException toThrow = null;

        T result = null;
        try {
            result = foregroundTask.call();
        } catch (final Throwable e) { // NOSONAR: Any exceptions & errors that occur needs to trigger onThrow.
            toThrow = new ParallelExecutionException(e);
            onThrow.run();
        }

        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (toThrow == null) {
                toThrow = new ParallelExecutionException(e);
                onThrow.run();
            } else {
                // if foregroundTask already threw an exception, we add this one as a suppressed exception
                toThrow.addSuppressed(e);
            }
        }

        // if any of the tasks threw an exception then we throw
        if (toThrow != null) {
            throw toThrow;
        }

        return result;
    }

    /**
     * Same as {@link #doParallel(Callable, Callable, Runnable)} where the onThrow task is a no-op
     */
    @Override
    public <T> T doParallel(final Callable<T> foregroundTask, final Callable<Void> backgroundTask)
            throws ParallelExecutionException {
        return doParallel(foregroundTask, backgroundTask, NOOP);
    }
}
