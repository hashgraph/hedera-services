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

package com.swirlds.virtualmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

class VirtualLongKeyTest {
    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("A VirtualLongKey compared against itself should compare to 0")
    void compareSameInstance() {
        final VirtualLongKey key = new TestLongKey(1234);
        //noinspection EqualsWithItself
        assertEquals(0, key.compareTo(key), "key should compare with itself");
    }

    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("Two equivalent VirtualLongKeys compare to 0")
    void compareEqualInstances() {
        final VirtualLongKey key1 = new TestLongKey(1234);
        final VirtualLongKey key2 = new TestLongKey(1234);
        assertEquals(0, key1.compareTo(key2), "keys should compare");
        assertEquals(0, key2.compareTo(key1), "keys should compare");
    }

    @SuppressWarnings({"ConstantConditions", "ResultOfMethodCallIgnored"})
    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("Null comparisons throw")
    void compareNullThrows() {
        final VirtualLongKey key = new TestLongKey(5678);
        assertThrows(NullPointerException.class, () -> key.compareTo(null), "Should throw according to spec");
    }

    private static final class TestLongKey implements VirtualLongKey {
        private final long value;

        public TestLongKey(final long value) {
            this.value = value;
        }

        @Override
        public long getClassId() {
            return 83829283;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public void serialize(final SerializableDataOutputStream out) throws IOException {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public void serialize(final ByteBuffer buffer) throws IOException {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public void deserialize(final ByteBuffer buffer, final int version) throws IOException {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public long getKeyAsLong() {
            return value;
        }
    }
}
