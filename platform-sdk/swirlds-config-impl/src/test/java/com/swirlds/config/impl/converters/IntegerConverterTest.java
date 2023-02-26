/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.config.impl.converters;

import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IntegerConverterTest {

    @Test
    public void convertNull() {
        // given
        final IntegerConverter converter = new IntegerConverter();

        // then
        Assertions.assertThrows(
                NullPointerException.class, () -> converter.convert(null), "Null values must always throw a NPE");
    }

    @ParameterizedTest
    @MethodSource("provideConversionChecks")
    public void conversionCheck(final String rawValue, final int expectedValue) {
        // given
        final IntegerConverter converter = new IntegerConverter();

        // when
        final int value = converter.convert(rawValue);

        // then
        Assertions.assertEquals(expectedValue, value, "All valid integers must be supported");
    }

    @Test
    public void convertOutOfRangeNumber() {
        // given
        final IntegerConverter converter = new IntegerConverter();
        final String rawValue = Integer.MAX_VALUE + "0";

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> converter.convert(rawValue),
                "Only the integer range must be supported");
    }

    @Test
    public void convertInvalid() {
        // given
        final IntegerConverter converter = new IntegerConverter();
        final String rawValue = "1.23";

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> converter.convert(rawValue),
                "Only valid int values must be supported");
    }

    private static Stream<Arguments> provideConversionChecks() {
        return Stream.of(
                Arguments.of("-7", -7),
                Arguments.of("7", 7),
                Arguments.of("0", 0),
                Arguments.of(Integer.MAX_VALUE + "", Integer.MAX_VALUE),
                Arguments.of(Integer.MIN_VALUE + "", Integer.MIN_VALUE));
    }
}
