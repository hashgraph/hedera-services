/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.internal.AbstractMultiQueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadBuilder;
import com.swirlds.common.threading.manager.ThreadManager;
import java.util.function.Consumer;

/**
 * Configures and builds a {@link MultiQueueThread}.
 */
public class MultiQueueThreadConfiguration
        extends AbstractMultiQueueThreadConfiguration<MultiQueueThreadConfiguration> {

    /**
     * Create a new multi thread queue configuration.
     *
     * @param threadBuilder
     * 		builds threads
     */
    public MultiQueueThreadConfiguration(final ThreadBuilder threadBuilder) {
        super(threadBuilder);
    }

    /**
     * Copy constructor.
     */
    private MultiQueueThreadConfiguration(final MultiQueueThreadConfiguration other) {
        super(other);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MultiQueueThreadConfiguration copy() {
        return new MultiQueueThreadConfiguration(this);
    }

    /**
     * Build a wrapped queue thread that is capable of handling multiple data types. Behaves more or less
     * like a regular queue thread, but with some helpful boilerplate code.
     *
     * @return a wrapped queue thread
     */
    public MultiQueueThread build() {
        return build(false);
    }

    /**
     * Build a wrapped queue thread that is capable of handling multiple data types. Behaves more or less
     * like a regular queue thread, but with some helpful boilerplate code.
     *
     * @param start
     * 		if true then automatically start the thread
     * @return a wrapped queue thread
     */
    public MultiQueueThread build(final boolean start) {
        return this.buildMultiQueue(start);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> MultiQueueThreadConfiguration addHandler(final Class<T> clazz, final Consumer<T> handler) {
        return super.addHandler(clazz, handler);
    }
}
