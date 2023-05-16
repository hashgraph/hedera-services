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

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

/**
 * A system transaction giving all avgPingMilliseconds stats (sent as ping time in microseconds)
 *
 * @deprecated to be removed once we no longer have to migrate events that may contain this transaction type
 */
@Deprecated
public final class SystemTransactionPing extends SystemTransaction {
    /** class identifier for the purposes of serialization */
    private static final long PING_CLASS_ID = 0xe98d3e2c500a6647L;
    /** current class version */
    private static final int PING_CLASS_VERSION = 1;

    private int[] avgPingMilliseconds;

    /**
     * No-argument constructor used by ConstructableRegistry
     */
    public SystemTransactionPing() {}

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
    public void serialize(final SerializableDataOutputStream dos) throws IOException {
        dos.writeIntArray(avgPingMilliseconds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        this.avgPingMilliseconds = in.readIntArray(SettingsCommon.maxAddressSizeAllowed);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return PING_CLASS_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return PING_CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return PING_CLASS_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSerializedLength() {
        return getArraySerializedLength(avgPingMilliseconds);
    }
}
