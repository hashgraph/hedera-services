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

package com.swirlds.config.impl.internal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigNumberUtilsTest {

    @Test
    public void compareInt() {
        // given
        final Integer value = Integer.valueOf(7);
        final Number number = Double.valueOf(3.5d);

        // when
        final int result = ConfigNumberUtils.compare(value, Integer.TYPE, number);

        Assertions.assertTrue(result > 0);
    }

    @Test
    public void compareLong() {
        // given
        final Long value = Long.valueOf(7L);
        final Number number = Double.valueOf(3.5d);

        // when
        final int result = ConfigNumberUtils.compare(value, Long.TYPE, number);

        Assertions.assertTrue(result > 0);
    }

    @Test
    public void compareFloat() {
        // given
        final Float value = Float.valueOf(7.0f);
        final Number number = Double.valueOf(3.5d);

        // when
        final int result = ConfigNumberUtils.compare(value, Float.TYPE, number);

        Assertions.assertTrue(result > 0);
    }

    @Test
    public void compareDouble() {
        // given
        final Double value = Double.valueOf(7.0d);
        final Number number = Double.valueOf(3.5d);

        // when
        final int result = ConfigNumberUtils.compare(value, Double.TYPE, number);

        Assertions.assertTrue(result > 0);
    }

    @Test
    public void compareByte() {
        // given
        final Byte value = Byte.valueOf((byte) 7);
        final Number number = Double.valueOf(3.5d);

        // when
        final int result = ConfigNumberUtils.compare(value, Byte.TYPE, number);

        Assertions.assertTrue(result > 0);
    }

    @Test
    public void compareShort() {
        // given
        final Short value = Short.valueOf((short) 7);
        final Number number = Double.valueOf(3.5d);

        // when
        final int result = ConfigNumberUtils.compare(value, Short.TYPE, number);

        Assertions.assertTrue(result > 0);
    }
}
