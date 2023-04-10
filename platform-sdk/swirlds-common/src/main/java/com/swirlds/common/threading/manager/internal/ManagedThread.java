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

package com.swirlds.common.threading.manager.internal;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A thread managed by a thread manager.
 */
public class ManagedThread extends Thread {

    private final Runnable throwIfNotStarted;

    /**
     * Create a new managed thread.
     *
     * @param runnable          the runnable that will be executed on the thread
     * @param throwIfNotStarted a method that is executed when the thread is started, should throw if the thread should
     *                          not be started
     */
    public ManagedThread(
            @NonNull final Runnable runnable,
            @NonNull final Runnable throwIfNotStarted) {
        super(runnable);
        this.throwIfNotStarted = throwIfNotStarted;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start() {
        throwIfNotStarted.run();
        super.start();
    }
}
