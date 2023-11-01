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

package com.swirlds.platform.state;

import static com.swirlds.logging.legacy.LogMarker.FREEZE;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.UptimeData;
import com.swirlds.logging.legacy.payload.SetFreezeTimePayload;
import com.swirlds.logging.legacy.payload.SetLastFrozenTimePayload;
import com.swirlds.platform.uptime.MutableUptimeData;
import com.swirlds.platform.uptime.UptimeDataImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Contains any data that is either read or written by the platform and the application
 */
public class DualStateImpl extends PartialMerkleLeaf implements PlatformDualState, SwirldDualState, MerkleLeaf {
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
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public UptimeData getUptimeData() {
        return uptimeData;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public MutableUptimeData getMutableUptimeData() {
        return uptimeData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFreezeTime(Instant freezeTime) {
        this.freezeTime = freezeTime;
        logger.info(FREEZE.getMarker(), "setFreezeTime: {}", () -> freezeTime);
        logger.info(FREEZE.getMarker(), () -> new SetFreezeTimePayload(freezeTime).toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant getFreezeTime() {
        return freezeTime;
    }

    protected void setLastFrozenTime(Instant lastFrozenTime) {
        this.lastFrozenTime = lastFrozenTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLastFrozenTimeToBeCurrentFreezeTime() {
        this.lastFrozenTime = freezeTime;
        logger.info(FREEZE.getMarker(), "setLastFrozenTimeToBeCurrentFreezeTime: {}", () -> lastFrozenTime);
        logger.info(FREEZE.getMarker(), () -> new SetLastFrozenTimePayload(freezeTime).toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final DualStateImpl dualState = (DualStateImpl) other;
        return Objects.equals(freezeTime, dualState.freezeTime)
                && Objects.equals(lastFrozenTime, dualState.lastFrozenTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(freezeTime, lastFrozenTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("freezeTime", freezeTime)
                .append("lastFrozenTime", lastFrozenTime)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInFreezePeriod(Instant consensusTime) {
        // if freezeTime is not set, or consensusTime is before freezeTime, we are not in a freeze period
        // if lastFrozenTime is equal to or after freezeTime, which means the nodes have been frozen once at/after the
        // freezeTime, we are not in a freeze period
        if (freezeTime == null || consensusTime.isBefore(freezeTime)) {
            return false;
        }
        // Now we should check whether the nodes have been frozen at the freezeTime.
        // when consensusTime is equal to or after freezeTime,
        // and lastFrozenTime is before freezeTime, we are in a freeze period.
        return lastFrozenTime == null || lastFrozenTime.isBefore(freezeTime);
    }
}
