/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual.schedule;

import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;

/**
 * A {@link com.swirlds.virtualmap.VirtualKey} for a {@link
 * ScheduleVirtualValue#equalityCheckKey()}.
 *
 * <p>This is currently used in a MerkleMap due to issues with virtual map in the 0.27 release. It
 * should be moved back to VirtualMap in 0.28.
 */
public final class ScheduleEqualityVirtualKey implements VirtualLongKey {

    static final long CLASS_ID = 0xcd76f4fba3967595L;
    static final int BYTES_IN_SERIALIZED_FORM = 8;

    public static final int CURRENT_VERSION = 1;

    private long value;

    public ScheduleEqualityVirtualKey() {
        this(-1);
    }

    public ScheduleEqualityVirtualKey(final long value) {
        this.value = value;
    }

    public static int sizeInBytes() {
        return BYTES_IN_SERIALIZED_FORM;
    }

    /** {@inheritDoc} */
    @Override
    public long getKeyAsLong() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(value);
    }

    void serialize(final WritableSequentialData out) {
        out.writeLong(value);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        value = in.readLong();
    }

    void deserialize(final ReadableSequentialData in) {
        value = in.readLong();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ScheduleEqualityVirtualKey that = (ScheduleEqualityVirtualKey) o;

        return value == that.value;
    }

    /**
     * Verifies if the content from {@code buffer} is equal to the content of this instance.
     *
     * @param buffer The buffer with data to be compared with this class.
     * @return {@code true} if the content from the buffer has the same data as this instance.
     *     {@code false}, otherwise.
     */
    boolean equalsTo(final BufferedData buffer) {
        return buffer.readLong() == this.value;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return (int) MiscUtils.perm64(value);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "ScheduleEqualityVirtualKey{" + "value=" + value + '}';
    }
}
