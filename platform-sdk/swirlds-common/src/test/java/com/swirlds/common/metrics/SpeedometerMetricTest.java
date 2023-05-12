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

package com.swirlds.common.metrics;

import static com.swirlds.common.metrics.Metric.DataType.FLOAT;
import static com.swirlds.common.metrics.Metric.ValueType.MAX;
import static com.swirlds.common.metrics.Metric.ValueType.MIN;
import static com.swirlds.common.metrics.Metric.ValueType.STD_DEV;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.common.metrics.MetricType.SPEEDOMETER;
import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.common.statistics.StatsBuffered;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Testing SpeedometerMetric")
class SpeedometerMetricTest {

    private final SpeedometerMetric sut = new SpeedometerMetric() {
        @Override
        public Double get(ValueType valueType) {
            return null;
        }

        @Override
        public double getHalfLife() {
            return 0;
        }

        @Override
        public void update(double value) {}

        @Override
        public void cycle() {}

        @Override
        public double get() {
            return 0;
        }

        @Override
        public String getCategory() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public String getUnit() {
            return null;
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public void reset() {}

        @Override
        public StatsBuffered getStatsBuffered() {
            return null;
        }
    };

    @Test
    void getMetricType() {
        assertThat(sut.getMetricType()).isEqualTo(SPEEDOMETER);
    }

    @Test
    void getDataType() {
        assertThat(sut.getDataType()).isEqualTo(FLOAT);
    }

    @Test
    void getValueTypes() {
        assertThat(sut.getValueTypes()).containsExactly(VALUE, MAX, MIN, STD_DEV);
    }
}
