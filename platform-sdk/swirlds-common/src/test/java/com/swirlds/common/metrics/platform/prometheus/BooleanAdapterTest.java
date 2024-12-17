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

package com.swirlds.common.metrics.platform.prometheus;

import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.GLOBAL;
import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.PLATFORM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.platform.PlatformFunctionGauge;
import com.swirlds.common.platform.NodeId;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.snapshot.Snapshot;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.Test;

class BooleanAdapterTest {

    private static final String CATEGORY = "CaTeGoRy";
    private static final String NAME = "NaMe";
    private static final String MAPPING_NAME = CATEGORY + "_" + NAME;
    private static final String DESCRIPTION = "DeScRiPtIoN";

    private static final String[] NODE_LABEL = new String[] {"node"};
    private static final String[] NODE_VALUE = new String[] {"1"};

    private static final double EPSILON = 1e-6;

    @Test
    void testCreateGlobalMetric() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final Metric metric = new PlatformFunctionGauge<>(
                new FunctionGauge.Config<>(CATEGORY, NAME, Boolean.class, () -> true).withDescription(DESCRIPTION));

        // when
        new BooleanAdapter(registry, metric, GLOBAL);

        // then
        final Collector.MetricFamilySamples mapping =
                registry.metricFamilySamples().nextElement();
        assertThat(mapping.type).isEqualTo(Collector.Type.GAUGE);
        assertThat(mapping.name).isEqualTo(MAPPING_NAME);
        assertThat(mapping.help).isEqualTo(DESCRIPTION);
        assertThat(mapping.unit).isEmpty();
    }

    @Test
    void testCreatePlatformMetric() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final Metric metric = new PlatformFunctionGauge<>(
                new FunctionGauge.Config<>(CATEGORY, NAME, Boolean.class, () -> true).withDescription(DESCRIPTION));

        // when
        new BooleanAdapter(registry, metric, PLATFORM);

        // then
        final Collector.MetricFamilySamples mapping =
                registry.metricFamilySamples().nextElement();
        assertThat(mapping.type).isEqualTo(Collector.Type.GAUGE);
        assertThat(mapping.name).isEqualTo(MAPPING_NAME);
        assertThat(mapping.help).isEqualTo(DESCRIPTION);
        assertThat(mapping.unit).isEmpty();
    }

    @Test
    void testCreateBrokenNamesMetric() {
        // given
        final String brokenName = ".- /%()";
        final CollectorRegistry registry = new CollectorRegistry();
        final Metric metric = new PlatformFunctionGauge<>(
                new FunctionGauge.Config<>(brokenName, brokenName, Boolean.class, () -> true));

        // when
        new BooleanAdapter(registry, metric, GLOBAL);

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
        final Metric metric = new PlatformFunctionGauge<>(
                new FunctionGauge.Config<>(CATEGORY, NAME, Boolean.class, () -> true).withDescription(DESCRIPTION));

        // then
        assertThatThrownBy(() -> new BooleanAdapter(null, metric, GLOBAL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BooleanAdapter(null, metric, PLATFORM)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BooleanAdapter(registry, null, GLOBAL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BooleanAdapter(registry, null, PLATFORM)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BooleanAdapter(registry, metric, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testUpdateGlobalMetric() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final PlatformFunctionGauge<Boolean> metric =
                new PlatformFunctionGauge<>(new FunctionGauge.Config<>(CATEGORY, NAME, Boolean.class, () -> true));
        final BooleanAdapter adapter = new BooleanAdapter(registry, metric, GLOBAL);

        // when
        adapter.update(Snapshot.of(metric), null);

        // then
        assertThat(registry.getSampleValue(MAPPING_NAME))
                .withFailMessage("Synchronizing the boolean-value (true -> 1.0) failed")
                .isCloseTo(1.0, offset(EPSILON));
    }

    @Test
    void testUpdatePlatformMetric() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final PlatformFunctionGauge<Boolean> metric =
                new PlatformFunctionGauge<>(new FunctionGauge.Config<>(CATEGORY, NAME, Boolean.class, () -> true));
        final BooleanAdapter adapter = new BooleanAdapter(registry, metric, PLATFORM);

        // when
        adapter.update(Snapshot.of(metric), NodeId.of(1L));

        // then
        assertThat(registry.getSampleValue(MAPPING_NAME, NODE_LABEL, NODE_VALUE))
                .isCloseTo(1.0, offset(EPSILON));
    }

    @Test
    void testUpdateWithNullParameters() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final PlatformFunctionGauge<Boolean> metric =
                new PlatformFunctionGauge<>(new FunctionGauge.Config<>(CATEGORY, NAME, Boolean.class, () -> true));
        final BooleanAdapter adapter = new BooleanAdapter(registry, metric, PLATFORM);
        final NodeId nodeId = NodeId.of(1L);

        // then
        assertThatThrownBy(() -> adapter.update(null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.update(null, nodeId)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testAdaptedUnitIsEmptyWhenConfigUnitIsSet() {
        // given
        final CollectorRegistry registry = new CollectorRegistry();
        final Metric metric =
                new PlatformFunctionGauge<>(new FunctionGauge.Config<>(CATEGORY, NAME, Boolean.class, () -> true)
                        .withUnit("AnUnit")
                        .withDescription(DESCRIPTION));

        // when
        new BooleanAdapter(registry, metric, PLATFORM);

        // then
        final Collector.MetricFamilySamples mapping =
                registry.metricFamilySamples().nextElement();
        assertThat(mapping.type).isEqualTo(Collector.Type.GAUGE);
        assertThat(mapping.name).isEqualTo(MAPPING_NAME);
        assertThat(mapping.help).isEqualTo(DESCRIPTION);
        assertThat(mapping.unit).isEmpty();
    }
}
