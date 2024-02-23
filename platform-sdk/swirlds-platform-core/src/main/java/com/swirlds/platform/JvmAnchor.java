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

package com.swirlds.platform;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Creates a non-daemon JVM thread that does nothing. Keeps the JVM alive if all other threads are daemon threads.
 */
public class JvmAnchor implements Startable, Stoppable {

    private final Thread thread;

    /**
     * Constructor.
     *
     * @param threadManager the thread manager
     */
    public JvmAnchor(@NonNull final ThreadManager threadManager) {
        thread = new ThreadConfiguration(threadManager)
                .setComponent("platform")
                .setThreadName("jvm-anchor")
                .setRunnable(this::run)
                .setDaemon(false)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        thread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        thread.interrupt();
    }

    /**
     * Runs and does nothing until interrupted.
     */
    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                SECONDS.sleep(Integer.MAX_VALUE);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
