package com.swirlds.merkledb;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A wrapper around {@link CompletableFuture} that supports interruption
 * when the CompletableFuture is canceled.
 *
 */
public class InterruptibleCompletableFuture<T> {

    /** The wrapped CompletableFuture */
    private volatile CompletableFuture<T> completableFuture;

    /** The thread currently executing the task, if any */
    private volatile Thread executingThread;

    /**
     * Submits a task for asynchronous execution and returns a CompletableFuture
     * representing the pending results of the task.
     * <p>
     * The task will be interrupted if the CompletableFuture is canceled.
     *
     * @param task the task to execute
     * @param executor the executor to use for asynchronous execution
     * @return the CompletableFuture representing the result of the task
     */
    public static <T> InterruptibleCompletableFuture<T> runAsyncInterruptibly(Callable<T> task, Executor executor) {
        InterruptibleCompletableFuture<T> interruptibleCompletableFuture = new InterruptibleCompletableFuture<>();
        interruptibleCompletableFuture.completableFuture = CompletableFuture.supplyAsync(() -> {
            interruptibleCompletableFuture.executingThread = Thread.currentThread();
            try {
                try {
                    return task.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } finally {
                interruptibleCompletableFuture.executingThread = null;
            }
        }, executor);
        return interruptibleCompletableFuture;
    }

    /**
     * Attempts to cancel the wrapped CompletableFuture and interrupts the
     * executing thread if {@code mayInterruptIfRunning} is true.
     *
     * @param mayInterruptIfRunning {@code true} if the thread executing the
     *                              task should be interrupted; otherwise, in-progress
     *                              tasks are allowed to complete
     * @return {@code false} if the task could not be canceled, typically
     * because it has already completed normally; {@code true} otherwise
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = completableFuture.cancel(true);
        if (cancelled && mayInterruptIfRunning && executingThread != null) {
            executingThread.interrupt();
        }
        return cancelled;
    }

    /**
     * Returns the wrapped CompletableFuture.
     *
     * @return the wrapped CompletableFuture
     */
    public CompletableFuture<T> asCompletableFuture() {
        return completableFuture;
    }
}
