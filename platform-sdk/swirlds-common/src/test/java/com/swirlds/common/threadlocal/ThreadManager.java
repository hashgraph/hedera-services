package com.swirlds.common.threadlocal;

import com.swirlds.base.context.Context;
import com.swirlds.base.state.Lifecycle;
import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;

public class ThreadManager implements ThreadFactory, ForkJoinWorkerThreadFactory, Lifecycle {

    private final NodeId id;
    private List<Thread> threads = new LinkedList<>();
    private List<ForkJoinPool> fjps = new LinkedList<>();

    public ThreadManager(final NodeId id) {
        this.id = id;
    }

    @Override
    public Thread newThread(final Runnable r) {
        final Thread t = new Thread(() -> {
            Context.getThreadLocalContext().add("nodeId", id.toString());
            r.run();
        });
        threads.add(t);
        return t;
    }


    // move to private inner class
    @Override
    public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
            return new NodeSpecificForkJoinWorkerThread(pool, id);
    }

    public ForkJoinPool newForkJoinPool(final int parallelism, final UncaughtExceptionHandler handler) {
        final ForkJoinPool fjp = new ForkJoinPool(parallelism, this, handler, false);
        fjps.add(fjp);
        return fjp;
    }

    @NonNull
    @Override
    public LifecyclePhase getLifecyclePhase() {
        return null;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }
}
