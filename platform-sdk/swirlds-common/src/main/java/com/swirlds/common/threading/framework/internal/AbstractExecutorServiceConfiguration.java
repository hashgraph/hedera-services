package com.swirlds.common.threading.framework.internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;

/**
 * A fluent style builder for executor services.
 */
public class AbstractExecutorServiceConfiguration<T extends AbstractExecutorServiceConfiguration<T>> {

    /**
     * Set the queue to be this size for an unlimited queue.
     */
    public static final int UNLIMITED_QUEUE_SIZE = -1; // TODO this doesn't belong here!

    private int threadCount = 1;
    private int queueSize = UNLIMITED_QUEUE_SIZE;
    private UncaughtExceptionHandler uncaughtExceptionHandler; // TODO default!

    public AbstractExecutorServiceConfiguration() {

    }

    /**
     * Set the number of threads for the pool. Default 1.
     *
     * @param threadCount the number of threads
     * @return this object
     */
    @SuppressWarnings("unchecked")
    @NonNull
    protected T setThreadCount(final int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("Thread count must be at least 1");
        }
        this.threadCount = threadCount;
        return (T) this;
    }

    /**
     * Get the configured number of threads.
     *
     * @return the configured number of threads
     */
    protected int getThreadCount() {
        return threadCount;
    }

    /**
     * Set the maximum queue size for work waiting to be processed. Default unlimited.
     *
     * @param queueSize the maximum queue size, or {@link #UNLIMITED_QUEUE_SIZE} if there should be no limit.
     * @return this object
     */
    @SuppressWarnings("unchecked")
    @NonNull
    protected T setQueueSize(final int queueSize) {
        if (queueSize < 1 && queueSize != UNLIMITED_QUEUE_SIZE) {
            throw new IllegalArgumentException("Queue size must be at least 1 or unlimited");
        }
        this.queueSize = queueSize;
        return (T) this;
    }

    /**
     * Get the configured queue size.
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
    @SuppressWarnings("unchecked")
    @NonNull
    protected T setUncaughtExceptionHandler(
            final @NonNull UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        return (T) this;
    }
}
