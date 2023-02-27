/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.QueueThreadPool;
import com.swirlds.common.threading.framework.ThreadSeed;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a thread pool that continuously takes elements from a queue and handles them.
 *
 * @param <T>
 * 		the type of the item in the queue
 */
public class QueueThreadPoolImpl<T> extends AbstractBlockingQueue<T> implements QueueThreadPool<T> {

    private final List<QueueThread<T>> threads = new ArrayList<>();

    /**
     * Build a new thread queue pool.
     *
     * @param configuration
     * 		configuration for the thread pool
     */
    protected QueueThreadPoolImpl(final AbstractQueueThreadPoolConfiguration<?, T> configuration) {
        super(configuration.getOrInitializeQueue());

        configuration.enableThreadNumbering();

        for (int threadIndex = 0; threadIndex < configuration.getThreadCount(); threadIndex++) {
            threads.add(configuration.copy().buildQueueThread(false));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ThreadSeed> buildSeeds() {
        final List<ThreadSeed> seeds = new ArrayList<>(threads.size());
        for (final QueueThread<T> thread : threads) {
            seeds.add(thread.buildSeed());
        }
        return seeds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        for (final QueueThread<T> thread : threads) {
            thread.start();
        }
    }

    /**
     * <p>Attempts to gracefully stop each thread in the pool</p>
     *
     * <p>Should not be called before the threads have been started, or in the case of a seed before the seed has been
     * built.</p>
     */
    @Override
    public boolean stop() {
        boolean success = true;
        for (final QueueThread<T> thread : threads) {
            success &= thread.stop();
        }
        return success;
    }

    /**
     * <p>Attempts to gracefully stop each thread in the pool</p>
     *
     * <p>Should not be called before the threads have been started, or in the case of a seed before the seed has been
     * built.</p>
     *
     * @param behavior
     * 		the type of {@link StopBehavior} that should be used to stop each thread
     * @return
     */
    @Override
    public boolean stop(final StopBehavior behavior) {
        boolean success = true;
        for (final QueueThread<T> thread : threads) {
            success &= thread.stop(behavior);
        }
        return success;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean pause() {
        boolean success = true;
        for (final QueueThread<T> thread : threads) {
            success &= thread.pause();
        }
        return success;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean resume() {
        boolean success = true;
        for (final QueueThread<T> thread : threads) {
            success &= thread.resume();
        }
        return success;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void join() throws InterruptedException {
        for (final QueueThread<T> thread : threads) {
            thread.join();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void join(final long millis) throws InterruptedException {
        for (final QueueThread<T> thread : threads) {
            thread.join(millis);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void join(final long millis, final int nanos) throws InterruptedException {
        for (final QueueThread<T> thread : threads) {
            thread.join(millis, nanos);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        if (threads.isEmpty()) {
            return "QueueThreadPool(0)";
        } else {
            return "QueueThreadPool(" + threads.get(0).getName() + ")";
        }
    }
}
