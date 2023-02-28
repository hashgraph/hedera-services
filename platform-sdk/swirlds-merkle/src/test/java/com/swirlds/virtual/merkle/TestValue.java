/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public final class TestValue implements VirtualValue {

    private String s;

    public TestValue() {}

    public TestValue(long path) {
        this("Value " + path);
    }

    public TestValue(String s) {
        this.s = s;
    }

    public String getValue() {
        return s;
    }

    @Override
    public long getClassId() {
        return 0x155bb9565ebfad3aL;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeNormalisedString(s);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        s = in.readNormalisedString(1024);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestValue other = (TestValue) o;
        return Objects.equals(s, other.s);
    }

    @Override
    public int hashCode() {
        return Objects.hash(s);
    }

    @Override
    public String toString() {
        return "TestValue{ " + s + " }";
    }

    @Override
    public void serialize(ByteBuffer buffer) throws IOException {
        final byte[] data = CommonUtils.getNormalisedStringBytes(this.s);
        buffer.putInt(data.length);
        buffer.put(data);
    }

    @Override
    public void deserialize(ByteBuffer buffer, int version) throws IOException {
        final int length = buffer.getInt();
        if (length > 1024) {
            throw new IOException("Bad data from buffer for string length. Value: " + length);
        }

        final byte[] data = new byte[length];
        buffer.get(data, 0, length);
        this.s = CommonUtils.getNormalisedStringFromBytes(data);
    }

    @Override
    public TestValue copy() {
        return new TestValue(s);
    }

    @Override
    public VirtualValue asReadOnly() {
        return this; // No setters on this thing, just don't deserialize...
    }
}
