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

package com.swirlds.common.metrics;

import static com.swirlds.metrics.api.Metric.DataType.FLOAT;
import static com.swirlds.metrics.api.Metric.ValueType.MAX;
import static com.swirlds.metrics.api.Metric.ValueType.MIN;
import static com.swirlds.metrics.api.Metric.ValueType.STD_DEV;
import static com.swirlds.metrics.api.Metric.ValueType.VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.metrics.api.MetricType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("Testing DurationGauge")
class DurationGaugeTest {

    private DurationGauge sut;

    @BeforeEach
    void setup() {
        sut = Mockito.mock(DurationGauge.class);
        when(sut.get(Mockito.any())).thenCallRealMethod();
        when(sut.getMetricType()).thenCallRealMethod();
        when(sut.getDataType()).thenCallRealMethod();
        when(sut.getValueTypes()).thenCallRealMethod();
    }

    @Test
    void getMetricType() {
        assertThat(sut.getMetricType()).isEqualTo(MetricType.GAUGE);
    }

    @Test
    void getDataType() {
        assertThat(sut.getDataType()).isEqualTo(FLOAT);
    }

    @Test
    void getValueTypes() {
        assertThat(sut.getValueTypes()).containsExactly(VALUE);
    }

    @Test
    void get_ShouldReturnValueByValueType() {

        final Double value = sut.get(VALUE);

        assertThat(value).isEqualTo(sut.get());
        verify(sut, times(2)).get();
    }

    @Test
    void get_ShouldThrowExceptionIfValueTypeNotSupported() {
        assertThatThrownBy(() -> sut.get(MAX)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sut.get(MIN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sut.get(STD_DEV)).isInstanceOf(IllegalArgumentException.class);
    }
}
