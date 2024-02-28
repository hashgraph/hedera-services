package com.swirlds.common.threadlocal;

import com.swirlds.base.context.Context;
import com.swirlds.common.platform.NodeId;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;

public class ThreadManager implements ThreadFactory, ForkJoinWorkerThreadFactory {

    private final NodeId id;
    private final boolean testMode;

    public ThreadManager(final NodeId id, final boolean testMode) {
        this.id = id;
        this.testMode = testMode;
    }

    @Override
    public Thread newThread(final Runnable r) {
        return new Thread(() -> {
            Context.getThreadLocalContext().add("nodeId", id.toString());
            r.run();
        });
    }

    @Override
    public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
        if (testMode) {
            return new NodeSpecificForkJoinWorkerThread(pool, id);
        }
        return ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
    }

    public ForkJoinPool newForkJoinPool(final int parallelism, final UncaughtExceptionHandler handler) {
        return new ForkJoinPool(parallelism, this, handler, false);
    }
}
