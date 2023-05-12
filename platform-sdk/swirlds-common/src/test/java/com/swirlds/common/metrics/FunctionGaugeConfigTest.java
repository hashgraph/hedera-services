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

package com.swirlds.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FunctionGaugeConfigTest {

    private static final String DEFAULT_FORMAT = "%s";

    private static final String CATEGORY = "CaTeGoRy";
    private static final String NAME = "NaMe";
    private static final String DESCRIPTION = "DeScRiPtIoN";
    private static final String UNIT = "UnIt";
    private static final String FORMAT = "FoRmAt";
    private static final Supplier<String> SUPPLIER = () -> "Hello World";

    @Test
    void testConstructor() {
        // when
        final FunctionGauge.Config<String> config = new FunctionGauge.Config<>(CATEGORY, NAME, String.class, SUPPLIER);

        // then
        assertThat(config.getCategory()).isEqualTo(CATEGORY);
        assertThat(config.getName()).isEqualTo(NAME);
        assertThat(config.getDescription()).isEqualTo(NAME);
        assertThat(config.getUnit()).isEmpty();
        assertThat(config.getFormat()).isEqualTo(DEFAULT_FORMAT);
        assertThat(config.getType()).isEqualTo(String.class);
        assertThat(config.getSupplier()).isEqualTo(SUPPLIER);
    }

    @Test
    @DisplayName("Constructor should throw IAE when passing illegal parameters")
    void testConstructorWithIllegalParameter() {
        assertThatThrownBy(() -> new FunctionGauge.Config<>(null, NAME, String.class, SUPPLIER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FunctionGauge.Config<>("", NAME, String.class, SUPPLIER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FunctionGauge.Config<>(" \t\n", NAME, String.class, SUPPLIER))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new FunctionGauge.Config<>(CATEGORY, null, String.class, SUPPLIER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FunctionGauge.Config<>(CATEGORY, "", String.class, SUPPLIER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FunctionGauge.Config<>(CATEGORY, " \t\n", String.class, SUPPLIER))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new FunctionGauge.Config<>(CATEGORY, NAME, null, SUPPLIER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FunctionGauge.Config<>(CATEGORY, NAME, String.class, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSetters() {
        // given
        final FunctionGauge.Config<String> config = new FunctionGauge.Config<>(CATEGORY, NAME, String.class, SUPPLIER);

        // when
        final FunctionGauge.Config<String> result =
                config.withDescription(DESCRIPTION).withUnit(UNIT).withFormat(FORMAT);

        // then
        assertThat(config.getCategory()).isEqualTo(CATEGORY);
        assertThat(config.getName()).isEqualTo(NAME);
        assertThat(config.getDescription()).isEqualTo(NAME);
        assertThat(config.getUnit()).isEmpty();
        assertThat(config.getFormat()).isEqualTo(DEFAULT_FORMAT);
        assertThat(config.getType()).isEqualTo(String.class);
        assertThat(config.getSupplier()).isEqualTo(SUPPLIER);

        assertThat(result.getCategory()).isEqualTo(CATEGORY);
        assertThat(result.getName()).isEqualTo(NAME);
        assertThat(result.getDescription()).isEqualTo(DESCRIPTION);
        assertThat(result.getUnit()).isEqualTo(UNIT);
        assertThat(result.getFormat()).isEqualTo(FORMAT);
        assertThat(result.getType()).isEqualTo(String.class);
        assertThat(result.getSupplier()).isEqualTo(SUPPLIER);
    }

    @Test
    void testSettersWithIllegalParameters() {
        // given
        final FunctionGauge.Config<String> config = new FunctionGauge.Config<>(CATEGORY, NAME, String.class, SUPPLIER);
        final String longDescription = DESCRIPTION.repeat(50);

        // then
        assertThatThrownBy(() -> config.withDescription(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withDescription("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withDescription(" \t\n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withDescription(longDescription)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withUnit(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withFormat(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withFormat("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withFormat(" \t\n")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        // given
        final FunctionGauge.Config<String> config = new FunctionGauge.Config<>(CATEGORY, NAME, String.class, SUPPLIER)
                .withDescription(DESCRIPTION)
                .withUnit(UNIT)
                .withFormat(FORMAT);

        // then
        assertThat(config.toString()).contains(CATEGORY, NAME, DESCRIPTION, UNIT, FORMAT, "java.lang.String");
    }
}
