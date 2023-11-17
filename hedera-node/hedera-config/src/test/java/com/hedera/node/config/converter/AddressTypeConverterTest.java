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

package com.hedera.node.config.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddressTypeConverterTest {

    private AddressTypeConverter subjectConverter;

    @BeforeEach
    void setUp() {
        subjectConverter = new AddressTypeConverter();
    }

    @Test
    void testNullParam() {
        assertThatThrownBy(() -> subjectConverter.convert(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNegativeNumberInvalidParam() {
        assertThatThrownBy(() -> subjectConverter.convert("-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidParam() {
        assertThatThrownBy(() -> subjectConverter.convert("someString")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void testInvalidSetParam() {
        assertThatThrownBy(() -> subjectConverter.convert("1062781,,10627811,111062787,invalid,1062787"))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void testValidParam() {
        // when
        final var besuAddress = subjectConverter.convert("1062787");
        // then
        assertThat(besuAddress).isEqualTo(Set.of(Address.fromHexString("0x0000000000000000000000000000000000103783")));
    }

    @Test
    void testValidParamSet() {
        // given
        final var value = "1062787,1062788";

        // when
        final var besuAddress = subjectConverter.convert(value);

        // then
        final var expectedSet = Set.of(
                Address.fromHexString("0x0000000000000000000000000000000000103783"),
                Address.fromHexString("0x0000000000000000000000000000000000103784"));
        assertThat(besuAddress).isEqualTo(expectedSet);
    }

    @Test
    void testValidParamSetWithBlank() {
        // given
        final var value = "1062789, ,10627890, ,1062791";

        // when
        final var besuAddress = subjectConverter.convert(value);

        // then
        final var expectedSet = Set.of(
                Address.fromHexString("0x0000000000000000000000000000000000103785"),
                Address.fromHexString("0x0000000000000000000000000000000000a22b32"),
                Address.fromHexString("0x0000000000000000000000000000000000103787"));
        assertThat(besuAddress).isEqualTo(expectedSet);
    }
}
