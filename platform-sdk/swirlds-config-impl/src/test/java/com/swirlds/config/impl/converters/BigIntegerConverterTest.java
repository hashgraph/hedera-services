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

import java.math.BigInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BigIntegerConverterTest {

    @Test
    public void convertNull() {
        // given
        final BigIntegerConverter converter = new BigIntegerConverter();

        // then
        Assertions.assertThrows(NullPointerException.class, () -> converter.convert(null));
    }

    @Test
    public void convert() {
        // given
        final BigIntegerConverter converter = new BigIntegerConverter();
        final String rawValue = "12";

        // when
        final BigInteger value = converter.convert(rawValue);

        // then
        Assertions.assertNotNull(value);
        Assertions.assertEquals("12", value.toString());
    }
}
