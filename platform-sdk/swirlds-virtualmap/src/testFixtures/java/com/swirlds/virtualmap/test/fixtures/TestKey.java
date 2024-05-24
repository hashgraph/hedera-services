/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.test.fixtures;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class TestKey implements VirtualLongKey {

    public static final int BYTES = Long.BYTES;

    private long k;

    private static final long CLASS_ID = 0x5491740ab996d4b1L;

    public TestKey() {}

    @Override
    public long getKeyAsLong() {
        return k;
    }

    public TestKey(long path) {
        this.k = path;
    }

    public TestKey(char s) {
        this.k = s;
    }

    public TestKey copy() {
        return new TestKey(k);
    }

    @Override
    public int getVersion() {
        return 1;
    }

    void serialize(final ByteBuffer buffer) {
        buffer.putLong(k);
    }

    void deserialize(final ByteBuffer buffer) {
        k = buffer.getLong();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(k);
    }

    @Override
    public String toString() {
        if (Character.isAlphabetic((char) k)) {
            return "TestKey{ " + ((char) k) + " }";
        } else {
            return "TestKey{ " + k + " }";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestKey other = (TestKey) o;
        return k == other.k;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    public void serialize(final WritableSequentialData out) {
        out.writeLong(k);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(k);
    }

    public void deserialize(final ReadableSequentialData in) {
        k = in.readLong();
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        k = in.readLong();
    }

    public static class Serializer implements KeySerializer<TestKey> {

        private static final long CLASS_ID = 0xce65e1e0f6774de7L;

        @Override
        public long getClassId() {
            return CLASS_ID;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public long getCurrentDataVersion() {
            return 1;
        }

        @Override
        public int getSerializedSize() {
            return TestKey.BYTES;
        }

        @Override
        public void serialize(final TestKey key, final WritableSequentialData out) {
            key.serialize(out);
        }

        @Override
        public TestKey deserialize(final ReadableSequentialData in) {
            final TestKey key = new TestKey();
            key.deserialize(in);
            return key;
        }

        @Override
        public boolean equals(final BufferedData buffer, final TestKey keyToCompare) {
            return keyToCompare.getKeyAsLong() == buffer.readLong();
        }
    }
}
