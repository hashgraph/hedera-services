/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.fcqueue;

import static com.swirlds.fcqueue.FCQueueStatistics.FCQUEUE_CATEGORY;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.metrics.PlatformMetricsFactory;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultPlatformMetrics;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.metrics.platform.PlatformMetricsFactoryImpl;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.metrics.api.Metrics;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class FCQueueStatisticsTest {

    @Test
    void test() {
        // given
        final MetricKeyRegistry registry = new MetricKeyRegistry();
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        final PlatformMetricsFactory factory = new PlatformMetricsFactoryImpl(metricsConfig);
        final Metrics metrics = new DefaultPlatformMetrics(null, registry, executor, factory, metricsConfig);

        // when
        FCQueueStatistics.register(metrics);

        // then
        assertTrue(FCQueueStatistics.isRegistered(), "FCQueueStatistics should be registered");
        final RunningAverageMetric fcqAddExecutionMicros =
                (RunningAverageMetric) metrics.getMetric(FCQUEUE_CATEGORY, "fcqAddExecMicroSec");
        final RunningAverageMetric fcqRemoveExecutionMicros =
                (RunningAverageMetric) metrics.getMetric(FCQUEUE_CATEGORY, "fcqRemoveExecMicroSec");
        final RunningAverageMetric fcqHashExecutionMicros =
                (RunningAverageMetric) metrics.getMetric(FCQUEUE_CATEGORY, "fcqHashExecMicroSec");

        // when
        FCQueueStatistics.updateFcqAddExecutionMicros(Math.PI);
        FCQueueStatistics.updateFcqRemoveExecutionMicros(Math.PI);
        FCQueueStatistics.updateFcqHashExecutionMicros(Math.PI);

        // then
        assertNotEquals(0, fcqAddExecutionMicros.get());
        assertNotEquals(0, fcqRemoveExecutionMicros.get());
        assertNotEquals(0, fcqHashExecutionMicros.get());
    }

    @Test
    void testRegisterWithNullParameter() {
        assertThrows(NullPointerException.class, () -> FCQueueStatistics.register(null));
    }
}
