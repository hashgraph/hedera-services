/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.DoubleAccumulator;
import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.IntegerAccumulator;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.metrics.LongGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.StatEntry;
import com.swirlds.common.statistics.StatsBuffered;
import com.swirlds.common.test.metrics.NoOpMetrics;
import com.swirlds.common.utility.NoOpMetricsBuilder;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("No-Op Metrics Test")
class NoOpMetricsTest {

    private void testCommonMethods(final Metric metric) {
        assertNotNull(metric.get(Metric.ValueType.VALUE));
        assertNotNull(metric.getCategory());
        assertNotNull(metric.getName());
        assertNotNull(metric.getDescription());
        assertNotNull(metric.getUnit());
        assertNotNull(metric.getFormat());
        assertNotNull(metric.getValueTypes());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Counter Test")
    void counterTest(final boolean source) {
        assertDoesNotThrow(() -> {
            final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
            final Counter metric = metrics.getOrCreate(new Counter.Config("asdf", "asdf"));

            metric.increment();

            metric.add(1);
            testCommonMethods(metric);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Double Accumulator Test")
    void doubleAccumulatorTest(final boolean source) {
        assertDoesNotThrow(() -> {
            final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
            final DoubleAccumulator metric = metrics.getOrCreate(new DoubleAccumulator.Config("asdf", "asdf"));

            metric.getInitialValue();
            metric.update(0);
            testCommonMethods(metric);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Double Gauge Test")
    void doubleGaugeTest(final boolean source) {
        assertDoesNotThrow(() -> {
            final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
            final DoubleGauge metric = metrics.getOrCreate(new DoubleGauge.Config("asdf", "asdf"));

            metric.get();
            metric.set(0);
            testCommonMethods(metric);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Duration Gauge Test")
    void durationGaugeTest(final boolean source) {
        assertDoesNotThrow(() -> {
            final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
            final DurationGauge metric =
                    metrics.getOrCreate(new DurationGauge.Config("asdf", "asdf", ChronoUnit.NANOS));

            metric.get();
            metric.getNanos();
            metric.set(Duration.ofSeconds(1));
            testCommonMethods(metric);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Function Gauge Test")
    void functionGaugeTest(final boolean source) {
        assertDoesNotThrow(() -> {
            final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
            final Supplier<Integer> supplier = () -> 0;
            final FunctionGauge<Integer> metric =
                    metrics.getOrCreate(new FunctionGauge.Config<>("asdf", "asdf", Integer.class, supplier));

            testCommonMethods(metric);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Integer Accumulator Test")
    void integerAccumulatorTest(final boolean source) {
        assertDoesNotThrow(() -> {
            final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
            final IntegerAccumulator metric = metrics.getOrCreate(new IntegerAccumulator.Config("asdf", "asdf"));

            metric.get();
            metric.getInitialValue();
            metric.update(0);
            testCommonMethods(metric);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Integer Gauge Test")
    void integerGaugeTest(final boolean source) {
        assertDoesNotThrow(() -> {
            final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
            final IntegerGauge metric = metrics.getOrCreate(new IntegerGauge.Config("asdf", "asdf"));

            metric.get();
            metric.set(0);
            testCommonMethods(metric);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Integer Pair Accumulator Test")
    void IntegerPairAccumulatorTest(final boolean source) {
        assertDoesNotThrow(() -> {
            final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
            final IntegerPairAccumulator<Integer> metric = metrics.getOrCreate(
                    new IntegerPairAccumulator.Config<>("asdf", "asdf", Integer.class, (x, y) -> 0));

            assertNotNull(metric.get());
            metric.getLeft();
            metric.getRight();
            metric.update(0, 0);
            assertNotNull(metric.getDataType());
            testCommonMethods(metric);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Long Accumulator Test")
    void longAccumulatorTest(final boolean source) {
        assertDoesNotThrow(() -> {
            final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
            final LongAccumulator metric = metrics.getOrCreate(new LongAccumulator.Config("asdf", "asdf"));

            metric.get();
            metric.getInitialValue();
            metric.update(0);
            testCommonMethods(metric);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Long Gauge Test")
    void longGaugeTest(final boolean source) {
        assertDoesNotThrow(() -> {
            final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
            final LongGauge metric = metrics.getOrCreate(new LongGauge.Config("asdf", "asdf"));

            metric.get();
            metric.set(0);
            testCommonMethods(metric);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Running Average Metric Test")
    void runningAverageMetricTest(final boolean source) {
        assertDoesNotThrow(() -> {
            final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
            final RunningAverageMetric metric = metrics.getOrCreate(new RunningAverageMetric.Config("asdf", "asdf"));

            metric.get(Metric.ValueType.VALUE);
            metric.get();
            metric.getHalfLife();
            metric.update(0);
            testCommonMethods(metric);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Speedometer Metric Test")
    void speedometerMetricTest(final boolean source) {
        assertDoesNotThrow(() -> {
            final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
            final SpeedometerMetric metric = metrics.getOrCreate(new SpeedometerMetric.Config("asdf", "asdf"));

            metric.get(Metric.ValueType.VALUE);
            metric.get();
            metric.getHalfLife();
            metric.update(0);
            metric.cycle();

            testCommonMethods(metric);
        });
    }

    @Test
    @DisplayName("Create Stat Entry Test")
    void createStatEntryTest() {
        assertDoesNotThrow(() -> {
            final Metrics metrics = new NoOpMetrics();
            final StatEntry metric =
                    metrics.getOrCreate(new StatEntry.Config<>("asdf", "asdf", Integer.class, () -> 0));

            assertNotNull(metric.getDataType());
            assertNotNull(metric.getStatsBuffered());
            assertNotNull(metric.getReset());
            metric.getReset().accept(0.0);
            assertNotNull(metric.getStatsStringSupplier());
            assertNotNull(metric.getStatsStringSupplier().get());
            assertNotNull(metric.getResetStatsStringSupplier());
            assertNotNull(metric.getResetStatsStringSupplier().get());
            testCommonMethods(metric);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("getMetric() Test")
    void getMetricTest(final boolean source) {
        final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();

        // no category or metric
        assertNull(metrics.getMetric("foo", "bar"));

        final Metric metric1 = metrics.getOrCreate(new Counter.Config("foo", "1"));
        assertNotNull(metric1);
        assertSame(metric1, metrics.getMetric("foo", "1"));

        // category exists, metric does not
        assertNull(metrics.getMetric("foo", "bar"));

        metrics.remove("foo", "bar");
        assertNull(metrics.getMetric("foo", "bar"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Find Metrics By Category Test")
    void findMetricsByCategoryTest(final boolean source) {
        final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();

        final Metric foo1 = metrics.getOrCreate(new Counter.Config("foo", "1"));
        final Metric foo2 = metrics.getOrCreate(new Counter.Config("foo", "2"));
        final Metric foo3 = metrics.getOrCreate(new Counter.Config("foo", "3"));

        final Metric bar1 = metrics.getOrCreate(new Counter.Config("bar", "1"));
        final Metric bar2 = metrics.getOrCreate(new Counter.Config("bar", "2"));

        final Metric baz1 = metrics.getOrCreate(new Counter.Config("baz", "1"));

        final Set<Metric> metricsFoo = new HashSet<>(metrics.findMetricsByCategory("foo"));
        assertEquals(3, metricsFoo.size());
        assertTrue(metricsFoo.contains(foo1));
        assertTrue(metricsFoo.contains(foo2));
        assertTrue(metricsFoo.contains(foo3));

        final Set<Metric> metricsBar = new HashSet<>(metrics.findMetricsByCategory("bar"));
        assertEquals(2, metricsBar.size());
        assertTrue(metricsBar.contains(bar1));
        assertTrue(metricsBar.contains(bar2));

        final Set<Metric> metricsBaz = new HashSet<>(metrics.findMetricsByCategory("baz"));
        assertEquals(1, metricsBaz.size());
        assertTrue(metricsBaz.contains(baz1));

        assertTrue(metrics.findMetricsByCategory("asdf").isEmpty());

        // Remove some metrics and test again
        metrics.remove("foo", "1");
        metrics.remove("bar", "1");
        metrics.remove("baz", "1");

        final Set<Metric> metricsFooReduced = new HashSet<>(metrics.findMetricsByCategory("foo"));
        assertEquals(2, metricsFooReduced.size());
        assertTrue(metricsFoo.contains(foo2));
        assertTrue(metricsFoo.contains(foo3));

        final Set<Metric> metricsBarReduced = new HashSet<>(metrics.findMetricsByCategory("bar"));
        assertEquals(1, metricsBarReduced.size());
        assertTrue(metricsBar.contains(bar2));

        assertTrue(metrics.findMetricsByCategory("baz").isEmpty());
        assertTrue(metrics.findMetricsByCategory("asdf").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("getAll() Test")
    void getAllTest(final boolean source) {
        final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();

        final Metric foo1 = metrics.getOrCreate(new Counter.Config("foo", "1"));
        final Metric foo2 = metrics.getOrCreate(new Counter.Config("foo", "2"));
        final Metric foo3 = metrics.getOrCreate(new Counter.Config("foo", "3"));

        final Metric bar1 = metrics.getOrCreate(new Counter.Config("bar", "1"));
        final Metric bar2 = metrics.getOrCreate(new Counter.Config("bar", "2"));

        final Metric baz1 = metrics.getOrCreate(new Counter.Config("baz", "1"));

        final Set<Metric> allMetrics = new HashSet<>(metrics.getAll());
        assertEquals(6, allMetrics.size());
        assertTrue(allMetrics.contains(foo1));
        assertTrue(allMetrics.contains(foo2));
        assertTrue(allMetrics.contains(foo3));
        assertTrue(allMetrics.contains(bar1));
        assertTrue(allMetrics.contains(bar2));
        assertTrue(allMetrics.contains(baz1));

        // Remove some metrics and test again
        metrics.remove("foo", "1");
        metrics.remove("bar", "1");
        metrics.remove("baz", "1");

        final Set<Metric> allMetricsReduced = new HashSet<>(metrics.getAll());
        assertEquals(3, allMetricsReduced.size());
        assertTrue(allMetricsReduced.contains(foo2));
        assertTrue(allMetricsReduced.contains(foo3));
        assertTrue(allMetricsReduced.contains(bar2));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("getOrCreate() Test")
    void getOrCreate(final boolean source) {
        final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();
        final Metric foo1 = metrics.getOrCreate(new Counter.Config("foo", "1"));
        assertSame(foo1, metrics.getOrCreate(new Counter.Config("foo", "1")));

        metrics.remove("foo", "1");

        final Metric foo1b = metrics.getOrCreate(new Counter.Config("foo", "1"));
        assertNotSame(foo1, foo1b);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Misc Metrics Test")
    void miscMetricsTest(final boolean source) {
        final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();

        // The following operations should not throw.
        assertDoesNotThrow(() -> {
            metrics.start();
            metrics.addUpdater(() -> {});
            metrics.removeUpdater(() -> {});
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("remove() Test")
    void removeTest(final boolean source) {
        final Metrics metrics = source ? new NoOpMetrics() : NoOpMetricsBuilder.buildNoOpMetrics();

        metrics.getOrCreate(new Counter.Config("foo", "1"));
        metrics.getOrCreate(new Counter.Config("foo", "2"));
        metrics.getOrCreate(new Counter.Config("foo", "3"));

        metrics.getOrCreate(new Counter.Config("bar", "1"));
        metrics.getOrCreate(new Counter.Config("bar", "2"));

        final Metric baz1 = metrics.getOrCreate(new Counter.Config("baz", "1"));

        metrics.remove("foo", "1");
        metrics.remove(new Counter.Config("bar", "1"));
        metrics.remove(baz1);

        assertNull(metrics.getMetric("foo", "1"));
        assertNull(metrics.getMetric("bar", "1"));
        assertNull(metrics.getMetric("baz", "1"));
    }
}
