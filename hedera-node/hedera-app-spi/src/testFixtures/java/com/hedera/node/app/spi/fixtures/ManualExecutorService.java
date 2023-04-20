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

package com.hedera.node.app.spi.fixtures;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An {@link ExecutorService} that provides control to the caller as exactly when to execute the tasks. This is useful
 * for unit tests, where you want to directly simulate background tasks happening at specific points in time.
 */
public class ManualExecutorService extends AbstractExecutorService {

    private LinkedList<Runnable> tasks = new LinkedList<>();
    private boolean shutdown = false;

    public void runAllTasks() {
        while (!tasks.isEmpty()) {
            tasks.removeFirst().run();
        }
    }

    public void runNextTask() {
        tasks.removeFirst().run();
    }

    @Override
    public void shutdown() {
        shutdown = true;
        tasks.clear();
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        final var remaining = new ArrayList<>(tasks);
        tasks.clear();
        return remaining;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return true;
    }

    @Override
    public void execute(Runnable command) {
        tasks.add(command);
    }
}
