/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hederahashgraph.fee;

import static com.hederahashgraph.fee.FeeUtils.clampedAdd;
import static com.hederahashgraph.fee.FeeUtils.clampedMultiply;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeUtilsTest {
    @Test
    void clampedAddWorks() {
        long a = 100L;
        long b = Long.MAX_VALUE;
        assertEquals(Long.MAX_VALUE, clampedAdd(a, b));

        b = 100L;
        assertEquals(200L, clampedAdd(a, b));

        a = -100L;
        b = Long.MIN_VALUE;
        assertEquals(Long.MIN_VALUE, clampedAdd(a, b));
    }

    @Test
    void clampedMultiplicationWorks() {
        long a = 100L;
        long b = Long.MAX_VALUE;
        assertEquals(Long.MAX_VALUE, clampedMultiply(a, b));

        b = 100L;
        assertEquals(10000L, clampedMultiply(a, b));

        a = -100L;
        b = Long.MAX_VALUE;
        assertEquals(Long.MIN_VALUE, clampedMultiply(a, b));
    }
}
