/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.framework.internal;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.ThreadSeed;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Implements a thread that continuously takes elements from a queue and handles them.
 *
 * @param <T>
 * 		the type of the item in the queue
 */
public class QueueThreadImpl<T> extends AbstractBlockingQueue<T> implements QueueThread<T> {

    private static final int WAIT_FOR_WORK_DELAY_MS = 10;

    private final int bufferSize;

    private final List<T> buffer;

    private final InterruptableConsumer<T> handler;

    private final StoppableThread stoppableThread;

    private final InterruptableRunnable waitForItemRunnable;

    private final AbstractQueueThreadConfiguration<?, T> configuration;

    /**
     * <p>
     * All instances of this class should be created via the appropriate configuration object.
     * </p>
     *
     * <p>
     * Unlike previous iterations of this class, this constructor DOES NOT start the background handler thread.
     * Call {@link #start()} to start the handler thread.
     * </p>
     *
     * @param configuration
     * 		the configuration object
     */
    public QueueThreadImpl(final AbstractQueueThreadConfiguration<?, T> configuration) {
        super(configuration.getOrInitializeQueue());

        this.configuration = configuration;

        final int capacity = configuration.getCapacity();

        if (capacity > 0) {
            bufferSize = Math.min(capacity, configuration.getMaxBufferSize());
        } else {
            bufferSize = configuration.getMaxBufferSize();
        }

        buffer = new ArrayList<>(bufferSize);

        handler = configuration.getHandler();

        this.waitForItemRunnable =
                Objects.requireNonNullElseGet(configuration.getWaitForItemRunnable(), () -> this::waitForItem);

        stoppableThread = configuration
                .setWork(this::doWork)
                .setStopBehavior(configuration.getStopBehavior())
                .setFinalCycleWork(this::doFinalCycleWork)
                .buildStoppableThread(false);
    }

    /**
     * <p>
     * Build a "seed" that can be planted in a thread. When the runnable is executed, it takes over the calling
     * thread
     * and configures that thread the way it would configure a newly created thread. When work
     * is finished, the calling thread is restored back to its original configuration.
     * </p>
     *
     * <p>
     * Note that this seed will be unable to change the thread group of the calling thread, regardless of the
     * thread group that is configured.
     * </p>
     *
     * <p>
     * This method should not be used if the queue thread has already been started.
     * </p>
     *
     * @return a seed that can be used to inject this thread configuration onto an existing thread.
     */
    @SuppressWarnings("unchecked")
    public ThreadSeed buildSeed() {
        if (((StoppableThreadImpl<?>) stoppableThread).hasBeenStartedOrInjected()) {
            throw new IllegalStateException(
                    "can not build seed for thread if it has already built a seed or if it has already been started");
        }

        return configuration.buildStoppableThreadSeed((StoppableThreadImpl<InterruptableRunnable>) stoppableThread);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        if (((StoppableThreadImpl<?>) stoppableThread).hasBeenStartedOrInjected()) {
            throw new IllegalStateException(
                    "can not start thread if it has already built a seed or if it has already been started");
        }

        stoppableThread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean pause() {
        return stoppableThread.pause();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean resume() {
        return stoppableThread.resume();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void join() throws InterruptedException {
        stoppableThread.join();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void join(final long millis) throws InterruptedException {
        stoppableThread.join(millis);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void join(final long millis, final int nanos) throws InterruptedException {
        stoppableThread.join(millis, nanos);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean stop() {
        return stoppableThread.stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean stop(final StopBehavior behavior) {
        return stoppableThread.stop(behavior);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean interrupt() {
        return stoppableThread.interrupt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAlive() {
        return stoppableThread.isAlive();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Status getStatus() {
        return stoppableThread.getStatus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isHanging() {
        return stoppableThread.isHanging();
    }

    /**
     * This method is called over and over by the stoppable thread.
     */
    private void doWork() throws InterruptedException {
        drainTo(buffer, bufferSize);
        if (buffer.size() == 0) {
            waitForItemRunnable.run();
            return;
        }

        for (final T item : buffer) {
            handler.accept(item);
        }
        buffer.clear();
    }

    /**
     * Wait a while for the next item to become available and handle it. If no item becomes available before
     * a timeout then return without doing any work.
     *
     * @throws InterruptedException
     * 		if this method is interrupted during execution
     */
    private void waitForItem() throws InterruptedException {
        final T item = poll(WAIT_FOR_WORK_DELAY_MS, TimeUnit.MILLISECONDS);
        if (item != null) {
            handler.accept(item);
        }
    }

    /**
     * {@inheritDoc}
     */
    private void doFinalCycleWork() throws InterruptedException {
        while (!isEmpty()) {
            drainTo(buffer, bufferSize);
            for (final T item : buffer) {
                handler.accept(item);
            }
            buffer.clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        if (stoppableThread.getStatus() != Status.ALIVE) {
            super.clear();
            return;
        }

        pause();
        super.clear();
        resume();
    }

    /**
     * Get the name of this thread.
     */
    @Override
    public String getName() {
        return stoppableThread.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE).append(getName()).toString();
    }
}
