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

import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import java.util.concurrent.ThreadFactory;

/**
 * A simple resource manager. The goal of this implementation is to create threads without complaining about lifecycle.
 * Eventually, this implementation should not be used in production code.
 */
public final class AdHocThreadManager implements ThreadManager {

    private static final AdHocThreadManager STATIC_THREAD_MANAGER = new AdHocThreadManager();

    /**
     * Get the static ad hoc thread manager. This manager does not complain about lifecycle violations.
     *
     * @return a thread manager that is always willing to create threads regardless of lifecycle
     */
    public static ThreadManager getStaticThreadManager() {
        return STATIC_THREAD_MANAGER;
    }

    private AdHocThreadManager() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        // intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        // intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Thread createThread(final ThreadGroup threadGroup, final Runnable runnable) {
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
    public LifecyclePhase getLifecyclePhase() {
        return LifecyclePhase.STARTED;
    }
}
