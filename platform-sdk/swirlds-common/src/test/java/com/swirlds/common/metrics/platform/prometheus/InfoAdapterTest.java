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

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.platform.DefaultFunctionGauge;
import com.swirlds.common.metrics.platform.Snapshot;
import com.swirlds.common.system.NodeId;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.Test;

class InfoAdapterTest {

    private static final String CATEGORY = "CaTeGoRy";
    private static final String NAME = "NaMe";
    private static final String MAPPING_NAME = CATEGORY + "_" + NAME;
    private static final String DESCRIPTION = "DeScRiPtIoN";

    private static final String[] GLOBAL_LABEL = new String[] {"value"};
    private static final String[] NODE_LABEL = new String[] {"node", "value"};
    private static final String[] NODE_VALUE = new String[] {"1"};

    private static final double EPSILON = 1e-6;

    @Test
    void testCreateGlobalMetric() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final Metric metric =
                new DefaultFunctionGauge<>(new FunctionGauge.Config<>(CATEGORY, NAME, String.class, () -> "Hello World")
                        .withDescription(DESCRIPTION));

        // when
        new InfoAdapter(registry, metric, GLOBAL);

        // then
        final Collector.MetricFamilySamples mapping =
                registry.metricFamilySamples().nextElement();
        assertThat(mapping.type).isEqualTo(Collector.Type.INFO);
        assertThat(mapping.name).isEqualTo(MAPPING_NAME);
        assertThat(mapping.help).isEqualTo(DESCRIPTION);
        assertThat(mapping.unit).isEmpty();
    }

    @Test
    void testCreatePlatformMetric() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final Metric metric =
                new DefaultFunctionGauge<>(new FunctionGauge.Config<>(CATEGORY, NAME, String.class, () -> "Hello World")
                        .withDescription(DESCRIPTION));

        // when
        new InfoAdapter(registry, metric, PLATFORM);

        // then
        final Collector.MetricFamilySamples mapping =
                registry.metricFamilySamples().nextElement();
        assertThat(mapping.type).isEqualTo(Collector.Type.INFO);
        assertThat(mapping.name).isEqualTo(MAPPING_NAME);
        assertThat(mapping.help).isEqualTo(DESCRIPTION);
        assertThat(mapping.unit).isEmpty();
    }

    @Test
    void testCreateBrokenNamesMetric() {
        // given
        final String brokenName = ".- /%";
        final CollectorRegistry registry = new CollectorRegistry();
        final Metric metric = new DefaultFunctionGauge<>(
                new FunctionGauge.Config<>(brokenName, brokenName, String.class, () -> "Hello World"));

        // when
        new InfoAdapter(registry, metric, GLOBAL);

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
        final Metric metric =
                new DefaultFunctionGauge<>(new FunctionGauge.Config<>(CATEGORY, NAME, String.class, () -> "Hello World")
                        .withDescription(DESCRIPTION));

        // then
        assertThatThrownBy(() -> new InfoAdapter(null, metric, GLOBAL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InfoAdapter(null, metric, PLATFORM))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InfoAdapter(registry, null, GLOBAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InfoAdapter(registry, null, PLATFORM))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InfoAdapter(registry, metric, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUpdateGlobalMetric() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final DefaultFunctionGauge<String> metric = new DefaultFunctionGauge<>(
                new FunctionGauge.Config<>(CATEGORY, NAME, String.class, () -> "Hello World"));
        final InfoAdapter adapter = new InfoAdapter(registry, metric, GLOBAL);

        // when
        adapter.update(Snapshot.of(metric), null);

        // then
        assertThat(registry.getSampleValue(MAPPING_NAME + "_info", GLOBAL_LABEL, new String[] {"Hello World"}))
                .isCloseTo(1.0, offset(EPSILON));
    }

    @Test
    void testUpdatePlatformMetric() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final DefaultFunctionGauge<String> metric = new DefaultFunctionGauge<>(
                new FunctionGauge.Config<>(CATEGORY, NAME, String.class, () -> "Hello World"));
        final InfoAdapter adapter = new InfoAdapter(registry, metric, PLATFORM);

        // when
        adapter.update(Snapshot.of(metric), NodeId.createMain(1L));

        // then
        assertThat(registry.getSampleValue(MAPPING_NAME + "_info", NODE_LABEL, new String[] {"1", "Hello World"}))
                .isCloseTo(1.0, offset(EPSILON));
    }

    @Test
    void testUpdateWithNullParameters() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final DefaultFunctionGauge<String> metric = new DefaultFunctionGauge<>(
                new FunctionGauge.Config<>(CATEGORY, NAME, String.class, () -> "Hello World"));
        final InfoAdapter adapter = new InfoAdapter(registry, metric, PLATFORM);
        final NodeId nodeId = NodeId.createMain(1L);

        // then
        assertThatThrownBy(() -> adapter.update(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.update(null, nodeId)).isInstanceOf(IllegalArgumentException.class);
    }
}
