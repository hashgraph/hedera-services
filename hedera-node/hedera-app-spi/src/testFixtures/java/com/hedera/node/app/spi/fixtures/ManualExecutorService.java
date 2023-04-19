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
