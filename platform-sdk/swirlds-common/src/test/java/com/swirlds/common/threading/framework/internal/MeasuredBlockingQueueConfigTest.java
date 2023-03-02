/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.framework.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsFactory;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Testing MeasuredBlockingQueue.Config")
class MeasuredBlockingQueueConfigTest {

    static final String CATEGORY = "myCategory";
    static final String QUEUE_NAME = "myName";

    private Metrics metrics;

    @BeforeEach
    void setUp() {
        final MetricKeyRegistry registry = new MetricKeyRegistry();
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final MetricsFactory factory = new DefaultMetricsFactory();
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        metrics = new DefaultMetrics(null, registry, executor, factory, metricsConfig);
    }

    @Test
    @DisplayName("Testing config constructor with max size metric enabled")
    void configConstructorWithMaxSizeEnabled() {
        final MeasuredBlockingQueue.Config config =
                new MeasuredBlockingQueue.Config(metrics, CATEGORY, QUEUE_NAME).withMaxSizeMetricEnabled(true);

        assertThat(config.getMetrics()).isEqualTo(metrics);
        assertThat(config.getCategory()).isEqualTo(CATEGORY);
        assertThat(config.getQueueName()).isEqualTo(QUEUE_NAME);
        assertThat(config.isMaxSizeMetricEnabled()).isTrue();
        assertThat(config.isMinSizeMetricEnabled()).isFalse();
        assertThat(config.isMetricEnabled()).isTrue();
    }

    @Test
    @DisplayName("Testing config constructor with min size metric enabled")
    void configConstructorWithMinSizeEnabled() {
        final MeasuredBlockingQueue.Config config =
                new MeasuredBlockingQueue.Config(metrics, CATEGORY, QUEUE_NAME).withMinSizeMetricEnabled(true);

        assertThat(config.getMetrics()).isEqualTo(metrics);
        assertThat(config.getCategory()).isEqualTo(CATEGORY);
        assertThat(config.getQueueName()).isEqualTo(QUEUE_NAME);
        assertThat(config.isMaxSizeMetricEnabled()).isFalse();
        assertThat(config.isMinSizeMetricEnabled()).isTrue();
        assertThat(config.isMetricEnabled()).isTrue();
    }

    @Test
    @DisplayName("Testing config constructor with no metric enabled")
    void configConstructorWithNoMetricEnabled() {
        final MeasuredBlockingQueue.Config config = new MeasuredBlockingQueue.Config(metrics, CATEGORY, QUEUE_NAME);

        assertThat(config.getMetrics()).isEqualTo(metrics);
        assertThat(config.getCategory()).isEqualTo(CATEGORY);
        assertThat(config.getQueueName()).isEqualTo(QUEUE_NAME);
        assertThat(config.isMaxSizeMetricEnabled()).isFalse();
        assertThat(config.isMinSizeMetricEnabled()).isFalse();
        assertThat(config.isMetricEnabled()).isFalse();
    }

    @Test
    @DisplayName("Testing config constructor with invalid parameter")
    void configConstructorWithInvalidParameter() {
        assertThatThrownBy(() -> new MeasuredBlockingQueue.Config(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MeasuredBlockingQueue.Config(null, CATEGORY, QUEUE_NAME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MeasuredBlockingQueue.Config(metrics, null, QUEUE_NAME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MeasuredBlockingQueue.Config(metrics, CATEGORY, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
