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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LongConverterTest {

    @Test
    public void convertNull() {
        // given
        final LongConverter converter = new LongConverter();

        // then
        Assertions.assertThrows(
                NullPointerException.class, () -> converter.convert(null), "Null values must always throw a NPE");
    }

    @Test
    public void convertMin() {
        // given
        final LongConverter converter = new LongConverter();
        final String rawValue = Long.MIN_VALUE + "";

        // when
        final long value = converter.convert(rawValue);

        // then
        Assertions.assertEquals(Long.MIN_VALUE, value, "All valid long values must be supported");
    }

    @Test
    public void convertMax() {
        // given
        final LongConverter converter = new LongConverter();
        final String rawValue = Long.MAX_VALUE + "";

        // when
        final long value = converter.convert(rawValue);

        // then
        Assertions.assertEquals(Long.MAX_VALUE, value, "All valid long values must be supported");
    }

    @Test
    public void convertZero() {
        // given
        final LongConverter converter = new LongConverter();
        final String rawValue = "0";

        // when
        final long value = converter.convert(rawValue);

        // then
        Assertions.assertEquals(0, value, "All valid long values must be supported");
    }

    @Test
    public void convertPositiveNumber() {
        // given
        final LongConverter converter = new LongConverter();
        final String rawValue = "21";

        // when
        final long value = converter.convert(rawValue);

        // then
        Assertions.assertEquals(21L, value, "All valid long values must be supported");
    }

    @Test
    public void convertNegativeNumber() {
        // given
        final LongConverter converter = new LongConverter();
        final String rawValue = "-7";

        // when
        final long value = converter.convert(rawValue);

        // then
        Assertions.assertEquals(-7L, value, "All valid long values must be supported");
    }

    @Test
    public void convertOutOfRangeNumber() {
        // given
        final LongConverter converter = new LongConverter();
        final String rawValue = Long.MAX_VALUE + "0";

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> converter.convert(rawValue),
                "Only the long value range must be supported");
    }

    @Test
    public void convertInvalid() {
        // given
        final LongConverter converter = new LongConverter();
        final String rawValue = "1.23";

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> converter.convert(rawValue),
                "Only valid long values must be supported");
    }
}
