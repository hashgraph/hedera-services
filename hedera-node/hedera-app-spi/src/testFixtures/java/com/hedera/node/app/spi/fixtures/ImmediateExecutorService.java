/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.fixtures;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * An {@link ExecutorService} that executes tasks immediately in the calling thread, rather than in a separate thread.
 * This is useful for synchronous testing where the API involves an {@link ExecutorService}.
 */
public class ImmediateExecutorService extends AbstractExecutorService {

    private boolean shutdown = false;

    @Override
    public synchronized void shutdown() {
        shutdown = true;
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        // Note, since this method is synchronized, it is not possible for another thread to concurrently add a new
        // task at the same time. And this method cannot be called while another thread is executing a task. So we can
        // safely return an empty list.
        shutdown = true;
        return Collections.emptyList();
    }

    @Override
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        // Since the methods on this class are synchronized, it is not possible for any execution of a task to happen
        // after or during shutdown, so as soon as we shut down, we are also immediately terminated.
        return isShutdown();
    }

    @Override
    public boolean awaitTermination(final long timeout, @NonNull final TimeUnit unit) throws InterruptedException {
        long millisToWait = unit.toMillis(timeout);
        while (!shutdown) {
            final long sleepTime = Math.min(10, millisToWait);
            //noinspection BusyWait
            Thread.sleep(sleepTime);
            millisToWait -= sleepTime;

            if (millisToWait <= 0) {
                return false;
            }
        }

        return true;
    }

    @Override
    public synchronized void execute(@NonNull final Runnable command) {
        if (shutdown) {
            throw new RejectedExecutionException();
        }

        command.run();
    }
}
