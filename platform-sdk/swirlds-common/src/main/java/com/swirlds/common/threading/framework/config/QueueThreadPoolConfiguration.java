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

package com.swirlds.common.threading.framework.config;

import com.swirlds.common.threading.framework.QueueThreadPool;
import com.swirlds.common.threading.framework.internal.AbstractQueueThreadPoolConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadBuilder;
import com.swirlds.common.threading.manager.ThreadManager;

/**
 * Implements a thread pool that continuously takes elements from a queue and handles them.
 *
 * @param <T>
 * 		the type of the item in the queue
 */
public class QueueThreadPoolConfiguration<T>
        extends AbstractQueueThreadPoolConfiguration<QueueThreadPoolConfiguration<T>, T> {

    /**
     * Create a new QueueThreadPool configuration.
     *
     * @param threadBuilder
     * 		responsible for building threads
     */
    public QueueThreadPoolConfiguration(final ThreadBuilder threadBuilder) {
        super(threadBuilder);
    }

    /**
     * Copy constructor.
     *
     * @param that
     * 		the configuration to copy.
     */
    private QueueThreadPoolConfiguration(final QueueThreadPoolConfiguration<T> that) {
        super(that);
    }

    /**
     * Get a copy of this configuration. New copy is always mutable,
     * and the mutability status of the original is unchanged.
     *
     * @return a copy of this configuration
     */
    @Override
    public QueueThreadPoolConfiguration<T> copy() {
        return new QueueThreadPoolConfiguration<>(this);
    }

    /**
     * <p>
     * Build a new queue thread pool. Does not start the pool.
     * </p>
     *
     * <p>
     * After calling this method, this configuration object should not be modified or used to construct other
     * pools.
     * </p>
     *
     * @return a queue thread built using this configuration
     */
    public QueueThreadPool<T> build() {
        return build(false);
    }

    /**
     * <p>
     * Build a new queue thread pool.
     * </p>
     *
     * <p>
     * After calling this method, this configuration object should not be modified or used to construct other
     * pools.
     * </p>
     *
     * @param start
     * 		if true then start the pool
     * @return a queue thread pool built using this configuration
     */
    public QueueThreadPool<T> build(final boolean start) {
        final QueueThreadPool<T> pool = buildQueueThreadPool(start);
        becomeImmutable();
        return pool;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * This method must be thread safe, as it will be invoked on multiple threads in parallel.
     * </p>
     */
    @Override
    public QueueThreadPoolConfiguration<T> setHandler(final InterruptableConsumer<T> handler) {
        return super.setHandler(handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InterruptableConsumer<T> getHandler() {
        return super.getHandler();
    }
}
