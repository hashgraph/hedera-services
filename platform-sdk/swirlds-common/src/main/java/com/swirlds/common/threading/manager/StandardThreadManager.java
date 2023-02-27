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

package com.swirlds.common.threading.manager;

import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.LifecyclePhase;
import java.util.concurrent.ThreadFactory;

/**
 * A standard implementation of a {@link ThreadManager}. Will not permit threads to be created
 * until {@link #start()} is called.
 */
public class StandardThreadManager implements ThreadManager {

    private LifecyclePhase phase = LifecyclePhase.NOT_STARTED;

    /**
     * {@inheritDoc}
     */
    @Override
    public Thread createThread(final ThreadGroup threadGroup, final Runnable runnable) {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        return new Thread(threadGroup, runnable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ThreadFactory createThreadFactory(final String component, final String threadName) {
        return new ThreadConfiguration(this)
                .setComponent(component)
                .setThreadName(threadName)
                .buildFactory();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfNotInPhase(LifecyclePhase.NOT_STARTED);
        phase = LifecyclePhase.STARTED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        phase = LifecyclePhase.STOPPED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LifecyclePhase getLifecyclePhase() {
        return phase;
    }
}
