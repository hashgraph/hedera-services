/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.stats;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultPlatformMetrics;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.metrics.platform.PlatformMetricsFactoryImpl;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.stats.simple.AccumulatedAverageTime;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AccumulatedAverageTimeTest {
    @Test
    void test() {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        final MetricKeyRegistry registry = mock(MetricKeyRegistry.class);
        when(registry.register(any(), any(), any())).thenReturn(true);
        final Metrics metrics = new DefaultPlatformMetrics(
                null,
                registry,
                mock(ScheduledExecutorService.class),
                new PlatformMetricsFactoryImpl(metricsConfig),
                metricsConfig);
        final AccumulatedAverageTime metric = new AccumulatedAverageTime(metrics, "category", "name", "description");
        Assertions.assertEquals(0d, metric.get(), "if no data is added, value should be 0");
        metric.add(Duration.ofMillis(10).toNanos());
        Assertions.assertEquals(10d, metric.get(), "one value was added, so that should be the average");
        metric.add(Duration.ofMillis(20).toNanos());
        Assertions.assertEquals(15d, metric.get(), "(10+20)/2 == 15");

        Assertions.assertEquals(15d, metric.get(), "no modification is expected");
    }
}
