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

import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        assertThatThrownBy(() -> subjectConverter.convert("1062781,10627811"))
                .isInstanceOf(NumberFormatException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "1062787, 0x0000000000000000000000000000000000103783",
        "1062786, 0x0000000000000000000000000000000000103782",
        "1072789, 0x0000000000000000000000000000000000105e95",
        "1061780, 0x0000000000000000000000000000000000103394",
        "1014787, 0x00000000000000000000000000000000000f7c03",
        "1052787, 0x0000000000000000000000000000000000101073",
        "1032787, 0x00000000000000000000000000000000000fc253",
    })
    void testValidParamSet(final String value, final String expectedOutput) {
        // when
        final var besuAddress = subjectConverter.convert(value);

        // then
        assertThat(besuAddress).isEqualTo(Address.fromHexString(expectedOutput));
    }
}
