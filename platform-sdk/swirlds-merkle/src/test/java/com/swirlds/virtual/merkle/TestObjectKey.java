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

package com.swirlds.virtual.merkle;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class TestObjectKey implements VirtualKey {

    public static final int BYTES = Long.BYTES * 2;

    private long k;

    public TestObjectKey() {}

    public TestObjectKey(long value) {
        this.k = value;
    }

    public TestObjectKey copy() {
        return new TestObjectKey(k);
    }

    @Override
    public int getVersion() {
        return 1;
    }

    long getValue() {
        return k;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(k);
        out.writeLong(k);
    }

    void serialize(final WritableSequentialData out) {
        out.writeLong(k);
        out.writeLong(k);
    }

    void serialize(final ByteBuffer buffer) {
        buffer.putLong(k);
        buffer.putLong(k);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        k = in.readLong();
        long kk = in.readLong();
        assert k == kk : "Malformed TestObjectKey";
    }

    void deserialize(final ReadableSequentialData in) {
        k = in.readLong();
        long kk = in.readLong();
        assert k == kk : "Malformed TestObjectKey";
    }

    void deserialize(final ByteBuffer buffer) {
        k = buffer.getLong();
        long kk = buffer.getLong();
        assert k == kk : "Malformed TestObjectKey";
    }

    @Override
    public int hashCode() {
        return Long.hashCode(k);
    }

    @Override
    public String toString() {
        if (Character.isAlphabetic((char) k)) {
            return "TestObjectKey{ " + ((char) k) + " }";
        } else {
            return "TestObjectKey{ " + k + " }";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestObjectKey other = (TestObjectKey) o;
        return k == other.k;
    }

    @Override
    public long getClassId() {
        return 0x255bb9565ebfad4bL;
    }
}
