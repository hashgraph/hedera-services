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

package com.swirlds.common.metrics.platform.prometheus;

import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.GLOBAL;
import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.PLATFORM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.platform.DefaultRunningAverageMetric;
import com.swirlds.common.metrics.platform.Snapshot;
import com.swirlds.common.system.NodeId;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.Test;

class DistributionAdapterTest {

    private static final String CATEGORY = "CaTeGoRy";
    private static final String NAME = "NaMe";
    private static final String MAPPING_NAME = "CaTeGoRy_NaMe";
    private static final String DESCRIPTION = "DeScRiPtIoN";
    private static final String UNIT = "UnIt";

    private static final String[] GLOBAL_LABEL = new String[] {"type"};
    private static final String[] NODE_LABEL = new String[] {"node", "type"};

    private static final double EPSILON = 1e-6;

    @Test
    void testCreateGlobalMetric() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final Metric metric = new DefaultRunningAverageMetric(new RunningAverageMetric.Config(CATEGORY, NAME)
                .withDescription(DESCRIPTION)
                .withUnit(UNIT));

        // when
        new DistributionAdapter(registry, metric, GLOBAL);

        // then
        final Collector.MetricFamilySamples mapping =
                registry.metricFamilySamples().nextElement();
        assertThat(mapping.type).isEqualTo(Collector.Type.GAUGE);
        assertThat(mapping.name).isEqualTo(MAPPING_NAME + "_" + UNIT);
        assertThat(mapping.help).isEqualTo(DESCRIPTION);
        assertThat(mapping.unit).isEqualTo(UNIT);
    }

    @Test
    void testCreatePlatformMetric() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final Metric metric = new DefaultRunningAverageMetric(new RunningAverageMetric.Config(CATEGORY, NAME)
                .withDescription(DESCRIPTION)
                .withUnit(UNIT));

        // when
        new DistributionAdapter(registry, metric, PLATFORM);

        // then
        final Collector.MetricFamilySamples mapping =
                registry.metricFamilySamples().nextElement();
        assertThat(mapping.type).isEqualTo(Collector.Type.GAUGE);
        assertThat(mapping.name).isEqualTo(MAPPING_NAME + "_" + UNIT);
        assertThat(mapping.help).isEqualTo(DESCRIPTION);
        assertThat(mapping.unit).isEqualTo(UNIT);
    }

    @Test
    void testCreateBrokenNamesMetric() {
        // given
        final String brokenName = ".- /%()";
        final CollectorRegistry registry = new CollectorRegistry();
        final Metric metric = new DefaultRunningAverageMetric(new RunningAverageMetric.Config(brokenName, brokenName));

        // when
        new DistributionAdapter(registry, metric, GLOBAL);

        // then
        final Collector.MetricFamilySamples mapping =
                registry.metricFamilySamples().nextElement();
        assertThat(mapping.name)
                .withFailMessage("Adjusting the name and category to Prometheus' requirements failed")
                .isEqualTo(":___per_Percent_:___per_Percent");
    }

    @Test
    void testConstructorWithNullParameters() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final Metric metric = new DefaultRunningAverageMetric(
                new RunningAverageMetric.Config(CATEGORY, NAME).withDescription(DESCRIPTION));

        // then
        assertThatThrownBy(() -> new DistributionAdapter(null, metric, GLOBAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DistributionAdapter(null, metric, PLATFORM))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DistributionAdapter(registry, null, GLOBAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DistributionAdapter(registry, null, PLATFORM))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DistributionAdapter(registry, metric, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUpdateGlobalMetric() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final DefaultRunningAverageMetric metric =
                new DefaultRunningAverageMetric(new RunningAverageMetric.Config(CATEGORY, NAME));
        metric.update(Math.PI);
        final DistributionAdapter adapter = new DistributionAdapter(registry, metric, GLOBAL);

        // when
        adapter.update(Snapshot.of(metric), null);

        // then
        assertThat(registry.getSampleValue(MAPPING_NAME, GLOBAL_LABEL, new String[] {"mean"}))
                .isCloseTo(Math.PI, offset(EPSILON));
        assertThat(registry.getSampleValue(MAPPING_NAME, GLOBAL_LABEL, new String[] {"min"}))
                .isCloseTo(Math.PI, offset(EPSILON));
        assertThat(registry.getSampleValue(MAPPING_NAME, GLOBAL_LABEL, new String[] {"max"}))
                .isCloseTo(Math.PI, offset(EPSILON));
        assertThat(registry.getSampleValue(MAPPING_NAME, GLOBAL_LABEL, new String[] {"stddev"}))
                .isCloseTo(0.0, offset(EPSILON));
    }

    @Test
    void testUpdatePlatformMetric() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final DefaultRunningAverageMetric metric =
                new DefaultRunningAverageMetric(new RunningAverageMetric.Config(CATEGORY, NAME));
        metric.update(Math.PI);
        final DistributionAdapter adapter = new DistributionAdapter(registry, metric, PLATFORM);

        // when
        adapter.update(Snapshot.of(metric), NodeId.createMain(1L));

        // then
        assertThat(registry.getSampleValue(MAPPING_NAME, NODE_LABEL, new String[] {"1", "mean"}))
                .isCloseTo(Math.PI, offset(EPSILON));
        assertThat(registry.getSampleValue(MAPPING_NAME, NODE_LABEL, new String[] {"1", "min"}))
                .isCloseTo(Math.PI, offset(EPSILON));
        assertThat(registry.getSampleValue(MAPPING_NAME, NODE_LABEL, new String[] {"1", "max"}))
                .isCloseTo(Math.PI, offset(EPSILON));
        assertThat(registry.getSampleValue(MAPPING_NAME, NODE_LABEL, new String[] {"1", "stddev"}))
                .isCloseTo(0.0, offset(EPSILON));
    }

    @Test
    void testUpdateWithNullParameters() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final DefaultRunningAverageMetric metric =
                new DefaultRunningAverageMetric(new RunningAverageMetric.Config(CATEGORY, NAME));
        final DistributionAdapter adapter = new DistributionAdapter(registry, metric, PLATFORM);
        final NodeId nodeId = NodeId.createMain(1L);

        // then
        assertThatThrownBy(() -> adapter.update(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.update(null, nodeId)).isInstanceOf(IllegalArgumentException.class);
    }
}
