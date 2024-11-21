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

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class TestKey implements VirtualKey {

    private long k;

    private static final long CLASS_ID = 0x5491740ab996d4b1L;

    public static Bytes longToKey(final long k) {
        final byte[] bytes = new byte[Long.BYTES];
        // sub-optimal, but easy
        ByteBuffer.wrap(bytes).putLong(k);
        return Bytes.wrap(bytes);
    }

    public static Bytes charToKey(final char c) {
        return longToKey(c);
    }

    public TestKey() {}

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

    long getKey() {
        return k;
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

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(k);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        k = in.readLong();
    }
}
