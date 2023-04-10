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

package com.swirlds.common.threading.framework.config;

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.internal.AbstractQueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadBuilder;
import com.swirlds.common.threading.manager.ThreadManager;

/**
 * An object used to configure and build {@link QueueThread}s.
 *
 * @param <T>
 * 		the type held by the queue
 */
public class QueueThreadConfiguration<T> extends AbstractQueueThreadConfiguration<QueueThreadConfiguration<T>, T> {

    /**
     * Build a new queue thread configuration with default values.
     *
     * @param threadBuilder
     * 		responsible for building threads
     */
    public QueueThreadConfiguration(final ThreadBuilder threadBuilder) {
        super(threadBuilder);
    }

    /**
     * Copy constructor.
     *
     * @param that
     * 		the configuration to copy.
     */
    public QueueThreadConfiguration(final QueueThreadConfiguration<T> that) {
        super(that);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public QueueThreadConfiguration<T> copy() {
        return new QueueThreadConfiguration<>(this);
    }

    /**
     * <p>
     * Build a new queue thread. Does not start the thread.
     * </p>
     *
     * <p>
     * After calling this method, this configuration object should not be modified or used to construct other
     * threads.
     * </p>
     *
     * @return a queue thread built using this configuration
     */
    public QueueThread<T> build() {
        return build(false);
    }

    /**
     * <p>
     * Build a new queue thread.
     * </p>
     *
     * <p>
     * After calling this method, this configuration object should not be modified or used to construct other
     * threads.
     * </p>
     *
     * @param start
     * 		if true then start the thread
     * @return a queue thread built using this configuration
     */
    public QueueThread<T> build(final boolean start) {
        final QueueThread<T> queueThread = buildQueueThread(start);
        becomeImmutable();
        return queueThread;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public QueueThreadConfiguration<T> setHandler(final InterruptableConsumer<T> handler) {
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
