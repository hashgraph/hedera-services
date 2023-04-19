package com.swirlds.common.utility;

import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.system.NodeId;
import com.swirlds.config.api.ConfigurationBuilder;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utilities for building no-op metrics.
 */
public final class NoOpMetricsBuilder {

    /**
     * An executor service that does nothing and doesn't start any threads. Required as a parameter default metrics.
     */
    private static final class NoOpScheduledExecutorService implements ScheduledExecutorService {

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return null;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                TimeUnit unit) {
            return null;
        }

        @Override
        public void shutdown() {

        }

        @Override
        public List<Runnable> shutdownNow() {
            return null;
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return null;
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return null;
        }

        @Override
        public Future<?> submit(Runnable task) {
            return null;
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
            return null;
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            return null;
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
            return null;
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            return null;
        }

        @Override
        public void execute(Runnable command) {

        }
    }

    /**
     * Build a {@link Metrics} implementation that does nothing.
     */
    public static Metrics buildNoOpMetrics() {
        final MetricsConfig configuration = ConfigurationBuilder
                .create()
                .withConfigDataType(MetricsConfig.class)
                .withSource(new SimpleConfigSource("disableMetricsOutput", "true"))
                .build()
                .getConfigData(MetricsConfig.class);

        return new DefaultMetrics(
                new NodeId(false, 0),
                new MetricKeyRegistry(),
                new NoOpScheduledExecutorService(),
                new DefaultMetricsFactory(),
                configuration);
    }
}
