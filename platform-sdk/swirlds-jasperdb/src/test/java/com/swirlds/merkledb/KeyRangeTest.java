/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyRangeTest {

    @Test
    void invalidRange() {
        assertThrows(IllegalArgumentException.class, () -> new KeyRange(20, 10), "Should have thrown");
    }

    @Test
    void createKeyRange() {
        final var keyRange = new KeyRange(10, 20);
        assertEquals(10, keyRange.getMinValidKey(), "Wrong min value");
        assertEquals(20, keyRange.getMaxValidKey(), "Wrong max value");
    }

    @Test
    void withinRange() {
        final var keyRange = new KeyRange(10, 20);
        assertFalse(keyRange.withinRange(-1), "Not within range");
        assertFalse(keyRange.withinRange(9), "Not within range");
        assertFalse(keyRange.withinRange(21), "Not within range");
        assertTrue(keyRange.withinRange(10), "Within range");
        assertTrue(keyRange.withinRange(11), "Within range");
        assertTrue(keyRange.withinRange(19), "Within range");
        assertTrue(keyRange.withinRange(20), "Within range");
    }

    @Test
    void testEquals() {
        final var keyRange1 = new KeyRange(10, 20);
        final var keyRange2 = new KeyRange(10, 20);
        assertEquals(keyRange1, keyRange2, "Keys should be equal");
        assertNotEquals(keyRange1, new KeyRange(9, 19), "Keys should not be equal");
    }

    @Test
    void testHashCode() {
        final var keyRange1 = new KeyRange(10, 20);
        final var keyRange2 = new KeyRange(10, 20);
        final var keyRange3 = new KeyRange(11, 20);
        assertEquals(keyRange1.hashCode(), keyRange2.hashCode(), "Should have the same hash code");
        assertNotEquals(keyRange1.hashCode(), keyRange3.hashCode(), "Should not have the same hash code");
    }

    @Test
    void testToString() {
        assertDoesNotThrow(() -> new KeyRange(123, 124).toString(), "Should not have thrown");
    }
}
