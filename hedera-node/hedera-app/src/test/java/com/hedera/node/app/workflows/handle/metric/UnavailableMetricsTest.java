/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.metric;

import static com.hedera.node.app.workflows.handle.metric.UnavailableMetrics.UNAVAILABLE_METRICS;
import static org.junit.jupiter.api.Assertions.*;

import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnavailableMetricsTest {
    @Mock
    private Metric metric;

    @Mock
    private MetricConfig<?, ?> config;

    @Test
    void noMethodIsSupported() {
        assertThrows(UnsupportedOperationException.class, () -> UNAVAILABLE_METRICS.getMetric("category", "name"));
        assertThrows(UnsupportedOperationException.class, () -> UNAVAILABLE_METRICS.findMetricsByCategory("category"));
        assertThrows(UnsupportedOperationException.class, UNAVAILABLE_METRICS::getAll);
        assertThrows(UnsupportedOperationException.class, () -> UNAVAILABLE_METRICS.getOrCreate(config));
        assertThrows(UnsupportedOperationException.class, () -> UNAVAILABLE_METRICS.remove("category", "name"));
        assertThrows(UnsupportedOperationException.class, () -> UNAVAILABLE_METRICS.remove(metric));
        assertThrows(UnsupportedOperationException.class, () -> UNAVAILABLE_METRICS.remove(config));
        assertThrows(UnsupportedOperationException.class, () -> UNAVAILABLE_METRICS.addUpdater(() -> {}));
        assertThrows(UnsupportedOperationException.class, () -> UNAVAILABLE_METRICS.removeUpdater(() -> {}));
        assertThrows(UnsupportedOperationException.class, UNAVAILABLE_METRICS::start);
    }
}
