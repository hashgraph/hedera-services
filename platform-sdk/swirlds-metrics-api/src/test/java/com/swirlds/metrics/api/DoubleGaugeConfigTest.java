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

package com.swirlds.metrics.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DoubleGaugeConfigTest {

    private static final String DEFAULT_FORMAT = FloatFormats.FORMAT_11_3;

    private static final String CATEGORY = "CaTeGoRy";
    private static final String NAME = "NaMe";
    private static final String DESCRIPTION = "DeScRiPtIoN";
    private static final String UNIT = "UnIt";
    private static final String FORMAT = "FoRmAt";

    private static final double EPSILON = 1e-6;

    @Test
    @DisplayName("Constructor should store values")
    void testConstructor() {
        // when
        final DoubleGauge.Config config = new DoubleGauge.Config(CATEGORY, NAME);

        // then
        assertThat(config.getCategory()).isEqualTo(CATEGORY);
        assertThat(config.getName()).isEqualTo(NAME);
        assertThat(config.getDescription()).isEqualTo(NAME);
        assertThat(config.getUnit()).isEmpty();
        assertThat(config.getFormat()).isEqualTo(DEFAULT_FORMAT);
        assertThat(config.getInitialValue()).isEqualTo(0.0, within(EPSILON));
        assertThat(config.getLabelKeys()).isEmpty();
        assertThat(config.getPredefinedLabels()).isEmpty();
    }

    @Test
    @DisplayName("Constructor should throw IAE when passing illegal parameters")
    void testConstructorWithIllegalParameter() {
        assertThatThrownBy(() -> new DoubleGauge.Config(null, NAME)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DoubleGauge.Config("", NAME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DoubleGauge.Config(" \t\n", NAME)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new DoubleGauge.Config(CATEGORY, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DoubleGauge.Config(CATEGORY, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DoubleGauge.Config(CATEGORY, " \t\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSetters() {
        // given
        final DoubleGauge.Config config = new DoubleGauge.Config(CATEGORY, NAME);

        // when
        final DoubleGauge.Config result = config.withDescription(DESCRIPTION)
                .withUnit(UNIT)
                .withFormat(FORMAT)
                .withInitialValue(Math.PI);

        // then
        assertThat(config.getCategory()).isEqualTo(CATEGORY);
        assertThat(config.getName()).isEqualTo(NAME);
        assertThat(config.getDescription()).isEqualTo(NAME);
        assertThat(config.getUnit()).isEmpty();
        assertThat(config.getFormat()).isEqualTo(DEFAULT_FORMAT);
        assertThat(config.getInitialValue()).isEqualTo(0.0, within(EPSILON));
        assertThat(config.getLabelKeys()).isEmpty();
        assertThat(config.getPredefinedLabels()).isEmpty();

        assertThat(result.getCategory()).isEqualTo(CATEGORY);
        assertThat(result.getName()).isEqualTo(NAME);
        assertThat(result.getDescription()).isEqualTo(DESCRIPTION);
        assertThat(result.getUnit()).isEqualTo(UNIT);
        assertThat(result.getFormat()).isEqualTo(FORMAT);
        assertThat(result.getInitialValue()).isEqualTo(Math.PI, within(EPSILON));
        assertThat(result.getLabelKeys()).isEmpty();
        assertThat(result.getPredefinedLabels()).isEmpty();
    }

    @Test
    void testSettersWithIllegalParameters() {
        // given
        final DoubleGauge.Config config = new DoubleGauge.Config(CATEGORY, NAME);
        final String longDescription = DESCRIPTION.repeat(50);

        // then
        assertThatThrownBy(() -> config.withDescription(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> config.withDescription("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withDescription(" \t\n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withDescription(longDescription)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withUnit(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> config.withFormat(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> config.withFormat("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withFormat(" \t\n")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        // given
        final DoubleGauge.Config config = new DoubleGauge.Config(CATEGORY, NAME)
                .withDescription(DESCRIPTION)
                .withUnit(UNIT)
                .withFormat(FORMAT)
                .withInitialValue(Math.PI);

        // then
        assertThat(config.toString()).contains(CATEGORY, NAME, DESCRIPTION, UNIT, FORMAT, "3.1415");
    }
}
