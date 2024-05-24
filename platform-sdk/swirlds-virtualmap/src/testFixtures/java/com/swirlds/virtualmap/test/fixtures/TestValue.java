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
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class TestValue implements VirtualValue {

    private String s;
    public boolean readOnly = false;
    private boolean released = false;

    public TestValue() {}

    public TestValue(long path) {
        this("Value " + path);
    }

    public TestValue(String s) {
        this.s = s;
    }

    @Override
    public long getClassId() {
        return 0x155bb9565ebfad3aL;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    public void setValue(String s) {
        assertMutable("setValue");
        assertNotReleased("setValue");
        this.s = s;
    }

    public int getProtoSizeInBytes() {
        return Integer.BYTES + s.getBytes(StandardCharsets.UTF_8).length;
    }

    public void protoSerialize(final WritableSequentialData out) {
        final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.writeBytes(bytes);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        assertNotReleased("serialize");
        out.writeNormalisedString(s);
    }

    public void protoDeserialize(final ReadableSequentialData in) {
        final int len = in.readInt();
        final byte[] bytes = new byte[len];
        in.readBytes(bytes);
        s = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        assertNotReleased("deserialize");
        assertMutable("deserialize");
        s = in.readNormalisedString(1024);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestValue other = (TestValue) o;
        return Objects.equals(s, other.s);
    }

    public String value() {
        assertNotReleased("fetch value");
        return s;
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
    public TestValue copy() {
        assertNotReleased("copy");
        readOnly = true;
        return new TestValue(s);
    }

    @Override
    public VirtualValue asReadOnly() {
        assertNotReleased("make readonly copy");
        TestValue value = new TestValue(s);
        value.readOnly = true;
        return value;
    }

    @Override
    public boolean release() {
        assertNotReleased("release");
        released = true;
        return true;
    }

    private void assertMutable(String action) {
        throwIfImmutable("Trying to " + action + " when already immutable.");
    }

    private void assertNotReleased(String action) {
        throwIfDestroyed("Trying to " + action + " when released.");
    }

    @Override
    public boolean isDestroyed() {
        return released;
    }

    @Override
    public boolean isImmutable() {
        return readOnly;
    }

    public static class Serializer implements ValueSerializer<TestValue> {

        private static final long CLASS_ID = 0x1f139bc81c6baf9fL;

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
            return VARIABLE_DATA_SIZE;
        }

        @Override
        public int getSerializedSize(final TestValue value) {
            return value.getProtoSizeInBytes();
        }

        @Override
        public int getTypicalSerializedSize() {
            return 32;
        }

        @Override
        public void serialize(final TestValue value, final WritableSequentialData out) {
            value.protoSerialize(out);
        }

        @Override
        public TestValue deserialize(final ReadableSequentialData in) {
            final TestValue value = new TestValue();
            value.protoDeserialize(in);
            return value;
        }
    }
}
