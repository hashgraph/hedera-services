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

public final class TestKey implements VirtualKey {

    public static final int BYTES = Long.BYTES;

    private long k;

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

    long getKey() {
        return k;
    }

    @Override
    public int getClassVersion() {
        return 1;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(k);
    }

    void serialize(final WritableSequentialData out) {
        out.writeLong(k);
    }

    void serialize(final ByteBuffer buffer) {
        buffer.putLong(k);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        k = in.readLong();
    }

    void deserialize(final ReadableSequentialData in) {
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
        return 0x155bb9565ebfad3bL;
    }
}
