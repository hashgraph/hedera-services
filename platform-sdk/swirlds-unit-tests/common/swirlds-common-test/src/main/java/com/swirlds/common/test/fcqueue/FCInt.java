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

package com.swirlds.common.test.fcqueue;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Objects;

/** a FastCopyable class that does nothing but store a single 4-byte int */
public class FCInt extends AbstractSerializableHashable implements FastCopyable {
    private static final long CLASS_ID = 0xe044c97abd231f8fL;
    private static final int CLASS_VERSION = 2;

    private int value;

    private boolean immutable;

    public FCInt() {}

    public FCInt(final int value) {
        this.value = value;
    }

    private FCInt(final FCInt value) {
        this.value = value.value;
        this.immutable = false;
        value.immutable = true;
    }

    public int getValue() {
        return value;
    }

    public void setValue(final int value) {
        this.value = value;
    }

    @Override
    public FCInt copy() {
        throwIfImmutable();
        return new FCInt(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeInt(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        value = in.readInt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isImmutable() {
        return this.immutable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        FCInt that = (FCInt) obj;
        return this.value == that.value;
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
