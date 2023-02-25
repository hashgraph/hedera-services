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

package com.swirlds.common.system.transaction.internal;

import static com.swirlds.common.io.streams.AugmentedDataOutputStream.getArraySerializedLength;
import static com.swirlds.common.system.transaction.SystemTransactionType.SYS_TRANS_BITS_PER_SECOND;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.transaction.SystemTransactionType;
import java.io.IOException;
import java.util.Arrays;

/** A system transaction giving all avgBytePerSecSent stats (sent as bits per second) */
public final class SystemTransactionBitsPerSecond extends SystemTransaction {
    /** class identifier for the purposes of serialization */
    private static final long BPS_CLASS_ID = 0x6922237d8f4dac99L;
    /** current class version */
    private static final int BPS_CLASS_VERSION = 1;

    private long[] avgBitsPerSecSent;

    /**
     * No-argument constructor used by ConstructableRegistry
     */
    public SystemTransactionBitsPerSecond() {}

    public SystemTransactionBitsPerSecond(final long[] avgBitsPerSecSent) {
        this.avgBitsPerSecSent = avgBitsPerSecSent;
    }

    /**
     * @return the long array of average bits per second
     */
    public long[] getAvgBitsPerSecSent() {
        return avgBitsPerSecSent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSize() {
        return getSerializedLength();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SystemTransactionType getType() {
        return SYS_TRANS_BITS_PER_SECOND;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream dos) throws IOException {
        dos.writeLongArray(avgBitsPerSecSent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        this.avgBitsPerSecSent = in.readLongArray(SettingsCommon.maxAddressSizeAllowed);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return BPS_CLASS_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return BPS_CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return BPS_CLASS_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSerializedLength() {
        return getArraySerializedLength(avgBitsPerSecSent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SystemTransactionBitsPerSecond that = (SystemTransactionBitsPerSecond) o;

        return Arrays.equals(avgBitsPerSecSent, that.avgBitsPerSecSent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(avgBitsPerSecSent);
    }
}
