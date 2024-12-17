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

package com.hedera.node.config.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.config.types.CongestionMultipliers;
import org.junit.jupiter.api.Test;

class CongestionMultipliersConverterTest {

    @Test
    void testNullValue() {
        // given
        final CongestionMultipliersConverter converter = new CongestionMultipliersConverter();

        // then
        assertThatThrownBy(() -> converter.convert(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testValidValue() {
        // given
        final CongestionMultipliersConverter converter = new CongestionMultipliersConverter();
        final String input = "90,10x,95,25x,99,100x";

        // when
        final CongestionMultipliers multipliers = converter.convert(input);

        // then
        assertThat(multipliers).isNotNull();
        assertThat(multipliers.usagePercentTriggers()).containsExactly(90, 95, 99);
        assertThat(multipliers.multipliers()).containsExactly(10L, 25L, 100L);
    }

    @Test
    void testInvalidValue() {
        // given
        final CongestionMultipliersConverter converter = new CongestionMultipliersConverter();

        // then
        assertThatThrownBy(() -> converter.convert("null")).isInstanceOf(IllegalArgumentException.class);
    }
}
