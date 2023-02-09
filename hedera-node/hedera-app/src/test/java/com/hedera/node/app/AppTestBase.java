package com.hedera.node.app;

import com.hedera.node.app.spi.fixtures.TestBase;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class AppTestBase extends TestBase {

    // For many of our tests we need to have metrics available, and an easy way to test the metrics
    // are being set appropriately.
    /** Used as a dependency to the {@link Metrics} system. */
    private static final ScheduledExecutorService METRIC_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor();

    /** Use this metrics object so we can inspect the metrics being set later in the tests */
    protected Metrics metrics = new DefaultMetrics(METRIC_EXECUTOR, new DefaultMetricsFactory());

    protected Counter counterMetric(String name) {
        return (Counter) metrics.getMetric("app", name);
    }

    protected SpeedometerMetric speedometerMetric(String name) {
        return (SpeedometerMetric) metrics.getMetric("app", name);
    }
}
