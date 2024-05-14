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

package com.swirlds.metrics.impl.test.noop;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.DoubleAccumulator;
import com.swirlds.metrics.api.DoubleGauge;
import com.swirlds.metrics.api.IntegerAccumulator;
import com.swirlds.metrics.api.IntegerGauge;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.metrics.api.LongGauge;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricsFactory;
import com.swirlds.metrics.impl.noop.NoOpMetricsFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        metric.reset();
    }

    @Test
    @DisplayName("Counter Test")
    void counterTest() {
        assertDoesNotThrow(() -> {
            final MetricsFactory metricsFactory = NoOpMetricsFactory.getInstance();
            final Counter metric = metricsFactory.createCounter(new Counter.Config("asdf", "asdf"));

            metric.increment();

            metric.add(0);
            testCommonMethods(metric);
        });
    }

    @Test
    @DisplayName("Double Accumulator Test")
    void doubleAccumulatorTest() {
        assertDoesNotThrow(() -> {
            final MetricsFactory metricsFactory = NoOpMetricsFactory.getInstance();
            final DoubleAccumulator metric =
                    metricsFactory.createDoubleAccumulator(new DoubleAccumulator.Config("asdf", "asdf"));

            metric.getInitialValue();
            metric.update(0);
            testCommonMethods(metric);
        });
    }

    @Test
    @DisplayName("Double Gauge Test")
    void doubleGaugeTest() {
        assertDoesNotThrow(() -> {
            final MetricsFactory metricsFactory = NoOpMetricsFactory.getInstance();
            final DoubleGauge metric = metricsFactory.createDoubleGauge(new DoubleGauge.Config("asdf", "asdf"));

            metric.get();
            metric.set(0);
            testCommonMethods(metric);
        });
    }

    @Test
    @DisplayName("Integer Accumulator Test")
    void integerAccumulatorTest() {
        assertDoesNotThrow(() -> {
            final MetricsFactory metricsFactory = NoOpMetricsFactory.getInstance();
            final IntegerAccumulator metric =
                    metricsFactory.createIntegerAccumulator(new IntegerAccumulator.Config("asdf", "asdf"));

            metric.get();
            metric.getInitialValue();
            metric.update(0);
            testCommonMethods(metric);
        });
    }

    @Test
    @DisplayName("Integer Gauge Test")
    void integerGaugeTest() {
        assertDoesNotThrow(() -> {
            final MetricsFactory metricsFactory = NoOpMetricsFactory.getInstance();
            final IntegerGauge metric = metricsFactory.createIntegerGauge(new IntegerGauge.Config("asdf", "asdf"));

            metric.get();
            metric.set(0);
            testCommonMethods(metric);
        });
    }

    @Test
    @DisplayName("Long Accumulator Test")
    void longAccumulatorTest() {
        assertDoesNotThrow(() -> {
            final MetricsFactory metricsFactory = NoOpMetricsFactory.getInstance();
            final LongAccumulator metric =
                    metricsFactory.createLongAccumulator(new LongAccumulator.Config("asdf", "asdf"));

            metric.get();
            metric.getInitialValue();
            metric.update(0);
            testCommonMethods(metric);
        });
    }

    @Test
    @DisplayName("Long Gauge Test")
    void longGaugeTest() {
        assertDoesNotThrow(() -> {
            final MetricsFactory metricsFactory = NoOpMetricsFactory.getInstance();
            final LongGauge metric = metricsFactory.createLongGauge(new LongGauge.Config("asdf", "asdf"));

            metric.get();
            metric.set(0);
            testCommonMethods(metric);
        });
    }
}
