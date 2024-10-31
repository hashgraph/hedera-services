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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
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
    public int getClassVersion() {
        return 1;
    }

    String getValue() {
        return s;
    }

    public void setValue(String s) {
        assertMutable("setValue");
        assertNotReleased("setValue");
        this.s = s;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        assertNotReleased("serialize");
        out.writeNormalisedString(s);
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
}
