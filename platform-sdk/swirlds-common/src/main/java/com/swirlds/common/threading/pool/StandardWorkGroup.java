/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.threading.futures.ConcurrentFuturePool;
import com.swirlds.common.threading.manager.ThreadManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A group of {@link Thread}s designed to support the following paradigm:
 *
 * 1) One or more threads are created to perform a task.
 * 2) Zero or more threads are created by those threads (or other descendant threads) to assist with the task.
 * 3) When the task is finished, all worker threads terminate.
 * 4) If any worker thread throws an exception, all threads stop and the exception is delivered to the calling context.
 */
public class StandardWorkGroup {

    private static final Logger logger = LogManager.getLogger(StandardWorkGroup.class);

    private static final String DEFAULT_TASK_NAME = "IDLE";

    private final String groupName;
    private final ExecutorService executorService;

    private final ConcurrentFuturePool<Void> futures;

    private volatile boolean hasExceptions;

    private final AtomicBoolean firstException = new AtomicBoolean(true);
    private final Runnable onException;

    /**
     * Create a new work group.
     *
     * @param threadManager
     * 		responsible for managing thread lifecycle
     * @param groupName
     * 		the name of the group
     * @param abortAction
     * 		if a non-<code>InterruptedException</code> exception is encountered, execute this method.
     * 		All threads in the work group are interrupted, but
     * 		if there is additional cleanup required then this method
     * 		can be used to perform that cleanup. Method is called at most
     * 		one time. If argument is null then no additional action is taken.
     */
    public StandardWorkGroup(final ThreadManager threadManager, final String groupName, final Runnable abortAction) {
        this.groupName = groupName;
        this.futures = new ConcurrentFuturePool<>(this::handleError);

        this.onException = abortAction;

        this.executorService = threadManager.createCachedThreadPool(
                "work group " + groupName + ": " + DEFAULT_TASK_NAME,
                (t, ex) -> logger.error(EXCEPTION.getMarker(), "Uncaught exception ", ex));
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    public boolean isTerminated() {
        return executorService.isTerminated();
    }

    /**
     * Perform an action on a thread managed by the work group. Any uncaught exception
     * (excluding {@link InterruptedException}) will be caught by the work group and will result
     * in the termination of all threads in the work group.
     *
     * @param operation
     * 		the method to run on the thread
     */
    @SuppressWarnings("unchecked")
    public void execute(final Runnable operation) {
        futures.add((Future<Void>) executorService.submit(operation));
    }

    /**
     * Perform an action on a thread managed by the work group. Any uncaught exception
     * (excluding {@link InterruptedException}) will be caught by the work group and will result
     * in the termination of all threads in the work group.
     *
     * @param taskName
     * 		used when naming the thread used by the work group
     * @param operation
     * 		the method to run on the thread
     */
    public void execute(final String taskName, final Runnable operation) {
        final Runnable wrapper = () -> {
            final String originalThreadName = Thread.currentThread().getName();
            final String newThreadName = originalThreadName.replaceFirst(DEFAULT_TASK_NAME, taskName);

            try {
                Thread.currentThread().setName(newThreadName);
                operation.run();
            } finally {
                Thread.currentThread().setName(originalThreadName);
            }
        };

        execute(wrapper);
    }

    public boolean hasExceptions() {
        return hasExceptions;
    }

    public void waitForTermination() throws InterruptedException {
        futures.waitForCompletion();
        executorService.shutdown();

        while (!executorService.isTerminated()) {
            if (executorService.awaitTermination(10, TimeUnit.MILLISECONDS)) {
                break;
            }
        }
    }

    /**
     * Pass an exception to the work group for handling. Will cause the work group to be torn down.
     *
     * @param ex
     * 		an exception
     */
    public void handleError(final Throwable ex) {
        if (!(ex instanceof InterruptedException)) {
            logger.error(EXCEPTION.getMarker(), "Work Group Exception [ groupName = {} ]", groupName, ex);
            hasExceptions = true;

            if (onException != null && firstException.getAndSet(false)) {
                onException.run();
            }

            executorService.shutdownNow();
        }
    }
}
