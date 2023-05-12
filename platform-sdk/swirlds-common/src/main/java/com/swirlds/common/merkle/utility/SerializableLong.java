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

package com.swirlds.common.merkle.utility;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * A long that is serializable.
 */
public class SerializableLong implements Comparable<SerializableLong>, FastCopyable, SelfSerializable {

    private static final long CLASS_ID = 0x70deca6058a40bc6L;

    private static class ClassVersion {

        public static final int ORIGINAL = 1;
    }

    private long value;

    /**
     * Create a new SerializableLong and set its value.
     *
     * @param value
     * 		the value for this object
     */
    public SerializableLong(final long value) {
        this.value = value;
    }

    /**
     * Create a new SerializableLong with a value of 0.
     */
    public SerializableLong() {}

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public SerializableLong copy() {
        return new SerializableLong(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(final SerializableLong that) {
        return Long.compare(this.value, that.value);
    }

    /**
     * Get the value.
     *
     * @return the value
     */
    public long getValue() {
        return this.value;
    }

    /**
     * Increment the value and return the result.
     *
     * @return the resulting value
     */
    public long getAndIncrement() {
        return value++;
    }

    /**
     * Decrement the value and return it.
     *
     * @return the resulting value
     */
    public long getAndDecrement() {
        return value--;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(this.value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        this.value = in.readLong();
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
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof SerializableLong)) {
            return false;
        }

        final SerializableLong that = (SerializableLong) o;
        return value == that.value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return Long.toString(value);
    }
}
