package com.swirlds.common.threadlocal;

import com.swirlds.common.platform.NodeId;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;

public class ThreadManagerWithContextTests {

    private final Logger logger = Loggers.getLogger(ThreadManagerWithContextTests.class);

    @Test
    void threadPoolTest() {
        final ThreadManager tm = new ThreadManager(new NodeId(0), true);
        final Executor e = Executors.newFixedThreadPool(5, tm);
        e.execute(() -> {
            logger.info("hello");
        });
    }

    @Test
    void dedicatedThreadTest() {
        final ThreadManager tm = new ThreadManager(new NodeId(0), true);
        final Thread thread = tm.newThread(() -> {
            logger.info("hello");
        });
        thread.start();
    }

    @Test
    void forkJoinPoolTest() {
        final UncaughtExceptionHandler exceptionHandler = (t, e) -> logger.error("uncaught exception in FJP", t);
        final Runnable logHello = () -> logger.info("hello");

        final ThreadManager threadManager0 = new ThreadManager(new NodeId(0), true);
        final ForkJoinPool pool0 = new ForkJoinPool(5, threadManager0, exceptionHandler, false);

        final ThreadManager threadManager1 = new ThreadManager(new NodeId(1), true);
        final ForkJoinPool pool1 = threadManager1.newForkJoinPool(5, exceptionHandler);

        pool0.execute(logHello);
        pool1.execute(logHello);
    }

}
