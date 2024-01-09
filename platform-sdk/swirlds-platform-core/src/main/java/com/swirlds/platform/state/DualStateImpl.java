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

package com.swirlds.platform.state;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.platform.uptime.UptimeDataImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Contains any data that is either read or written by the platform and the application
 * @deprecated can be removed after we don't need it for migration
 */
@Deprecated(forRemoval = true)
public class DualStateImpl extends PartialMerkleLeaf implements MerkleLeaf {
    private static final Logger logger = LogManager.getLogger(DualStateImpl.class);

    public static final long CLASS_ID = 0x565e2e04ce3782b8L;

    private static final class ClassVersion {
        private static final int ORIGINAL = 1;
        private static final int UPTIME_DATA = 2;
    }

    /**
     * the time when the freeze starts
     */
    private Instant freezeTime;

    /**
     * the last freezeTime based on which the nodes were frozen
     */
    private Instant lastFrozenTime;

    /**
     * Data on node uptime.
     */
    private UptimeDataImpl uptimeData = new UptimeDataImpl();

    public DualStateImpl() {}

    private DualStateImpl(@NonNull final DualStateImpl that) {
        super(that);
        this.freezeTime = that.freezeTime;
        this.lastFrozenTime = that.lastFrozenTime;
        this.uptimeData = that.uptimeData.copy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeInstant(freezeTime);
        out.writeInstant(lastFrozenTime);
        out.writeSerializable(uptimeData, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        freezeTime = in.readInstant();
        lastFrozenTime = in.readInstant();
        if (version >= ClassVersion.UPTIME_DATA) {
            uptimeData = in.readSerializable(false, UptimeDataImpl::new);
        }
    }

    /**
     * Get the node uptime data.
     */
    @NonNull
    public UptimeDataImpl getUptimeData() {
        return uptimeData;
    }

    /**
     * Get the freeze time.
     */
    public Instant getFreezeTime() {
        return freezeTime;
    }

    /**
     * Get the last frozen time.
     */
    public Instant getLastFrozenTime() {
        return lastFrozenTime;
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
        return ClassVersion.UPTIME_DATA;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DualStateImpl copy() {
        return new DualStateImpl(this);
    }
}
