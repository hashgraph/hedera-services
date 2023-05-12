/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.framework;

/**
 * A thread or a class with a thread that can be stopped.
 */
public interface StoppableThread extends Stoppable {

    /**
     * The current desired status for this thread.
     */
    enum Status {
        NOT_STARTED,
        ALIVE,
        PAUSED,
        DYING,
        DEAD
    }

    /**
     * The name of this thread.
     *
     * @return the name
     */
    String getName();

    /**
     * <p>
     * Build a "seed" that can be planted in a thread. When the runnable is executed, it takes over the calling thread
     * and configures that thread the way it would configure a newly created thread. When work
     * is finished, the calling thread is restored back to its original configuration.
     * </p>
     *
     * <p>
     * Note that this seed will be unable to change the thread group or daemon status of the calling thread,
     * regardless of configuration.
     * </p>
     *
     * <p>
     * Should only be called once. Should not be called if {@link #start()} has been called.
     * </p>
     *
     * @return a seed that can be used to inject this thread configuration onto an existing thread.
     */
    ThreadSeed buildSeed();

    /**
     * <p>
     * Interrupt this thread.
     * </p>
     *
     * <p>
     * If called before the thread/seed is started, then this method blocks until the thread/seed is started.
     * </p>
     */
    boolean interrupt();

    /**
     * Check if this thread is currently alive.
     *
     * @return true if this thread is alive
     */
    boolean isAlive();

    /**
     * Get the current status of the thread.
     *
     * @return the current status
     */
    Status getStatus();

    /**
     * Check if this thread is currently in a hanging state.
     *
     * @return true if this thread is currently in a hanging state
     */
    boolean isHanging();
}
