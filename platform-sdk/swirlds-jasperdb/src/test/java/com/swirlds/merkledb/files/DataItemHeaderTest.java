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

package com.swirlds.merkledb.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.swirlds.merkledb.serialize.DataItemHeader;
import org.junit.jupiter.api.Test;

class DataItemHeaderTest {

    /** the size of bytes for the data item, this includes the data item header. */
    private final int sizeBytes = 1;
    /** the key for data item, the key may be smaller than long up to size of long */
    private final long key = 2L;

    @Test
    void equalsIncorporatesAllFields() {
        final DataItemHeader base = new DataItemHeader(sizeBytes, key);
        final DataItemHeader differentKey = new DataItemHeader(sizeBytes, key + 1);
        final DataItemHeader differentSizeBytes = new DataItemHeader(sizeBytes + 1, key);
        final DataItemHeader otherButSame = base;
        final DataItemHeader otherButEqual = new DataItemHeader(sizeBytes, key);

        assertEquals(base, otherButSame, "Same headers are equal");
        assertEquals(base, otherButEqual, "Equivalent headers are equal");
        assertNotEquals(base, differentKey, "Different keys are unequal");
        assertNotEquals(base, differentSizeBytes, "Different sizes are unequal");
        assertNotEquals(base, new Object(), "Radically different objects are unequal");
    }

    @Test
    void hashCodesDiffer() {
        final DataItemHeader base = new DataItemHeader(sizeBytes, key);
        final DataItemHeader differentKey = new DataItemHeader(sizeBytes, key + 1);

        assertNotEquals(base.hashCode(), differentKey.hashCode(), "Different items have different hash codes");
    }
}
