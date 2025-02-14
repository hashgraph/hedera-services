// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.reconnect;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class TestKey implements VirtualKey {

    public static final int BYTES = Long.BYTES;

    private long k;

    private static final long CLASS_ID = 0x142f067b7698810dL;

    public TestKey() {}

    long getKey() {
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

    public void serialize(final WritableSequentialData out) {
        out.writeLong(k);
    }

    void serialize(final ByteBuffer buffer) {
        buffer.putLong(k);
    }

    public void deserialize(final ReadableSequentialData in) {
        k = in.readLong();
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
