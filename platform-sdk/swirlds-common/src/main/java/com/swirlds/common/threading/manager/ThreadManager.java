/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import com.swirlds.base.state.Lifecycle;
import com.swirlds.base.state.LifecycleException;
import com.swirlds.base.state.MutabilityException;
import java.util.concurrent.ThreadFactory;

/**
 * Responsible for managing threading resources.
 */
public interface ThreadManager extends Lifecycle {

    /**
     * Create a new thread. Thread is not automatically started.
     *
     * @param threadGroup the thread group into which the thread is placed
     * @param runnable    the runnable that will be executed on the thread
     * @return a new Thread
     * @throws LifecycleException if called before the thread manager has been started
     */
    Thread createThread(ThreadGroup threadGroup, Runnable runnable);

    /**
     * Create a new thread factory. Thread factory will throw {@link MutabilityException MutabilityException} if it is
     * used to create a thread before the thread manager is started.
     *
     * @param component  the component name
     * @param threadName the thread name (each thread created by the factory will also have a unique number appended to
     *                   the thread name)
     * @return a thread factory
     */
    ThreadFactory createThreadFactory(final String component, final String threadName);
}
