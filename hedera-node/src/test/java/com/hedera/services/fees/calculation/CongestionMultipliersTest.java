/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.calculation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CongestionMultipliersTest {
    @Test
    void readsExpectedValues() {
        final var prop = "90,10x,95,25x,99,100x";

        final var cm = CongestionMultipliers.from(prop);

        assertArrayEquals(new int[] {90, 95, 99}, cm.usagePercentTriggers());
        assertArrayEquals(new long[] {10L, 25L, 100L}, cm.multipliers());
    }

    @Test
    void roundsDownFloats() {
        final var prop = "90,10x,95,25x,99,100x";
        final var floatProp = "90.1,10x,95.8,25x,99.5,100x";

        final var cm = CongestionMultipliers.from(prop);
        final var cmFloat = CongestionMultipliers.from(floatProp);

        assertEquals(cm, cmFloat);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "90,10x,95,25x,99,-100",
                "90,10x,95,25x,99",
                "90,x,95,25x,99,100x",
                "90,10x,950,25x,99,100x",
                "90,10x,95,25x,,100x",
                "90,10x,95,25x,99x,100x",
                "90,10y,95,25x,99,100x",
                "90,10,95,25,94,100",
                "90,10,95,25,99,24"
            })
    void throwsOnMalformedProps(final String prop) {
        assertThrows(IllegalArgumentException.class, () -> CongestionMultipliers.from(prop));
    }

    @Test
    void objectContractsMet() {
        final var propA = "90,10x,95,25x,99,100x";
        final var propB = "90,10x,95,25x,99,1000x";
        final var propC = "90,10x,94,25x,99,100x";

        final var a = CongestionMultipliers.from(propA);

        assertEquals(a, CongestionMultipliers.from(propA));
        assertNotEquals(null, a);
        assertNotEquals(new Object(), a);
        assertNotEquals(a, CongestionMultipliers.from(propB));
        assertNotEquals(a, CongestionMultipliers.from(propC));

        assertEquals(a.hashCode(), a.hashCode());
        assertEquals(a.hashCode(), CongestionMultipliers.from(propA).hashCode());
        assertNotEquals(new Object().hashCode(), a.hashCode());
        assertNotEquals(a.hashCode(), CongestionMultipliers.from(propB).hashCode());
        assertNotEquals(a.hashCode(), CongestionMultipliers.from(propC).hashCode());
    }

    @Test
    void toStringWorks() {
        final var propA = "90,10x,95,25x,99,100x";
        final var desired = "CongestionMultipliers{10x @ >=90%, 25x @ >=95%, 100x @ >=99%}";

        assertEquals(desired, CongestionMultipliers.from(propA).toString());
    }
}
