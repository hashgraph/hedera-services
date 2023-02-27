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

import static com.swirlds.logging.LogMarker.FREEZE;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.logging.payloads.SetFreezeTimePayload;
import com.swirlds.logging.payloads.SetLastFrozenTimePayload;
import java.io.IOException;
import java.time.Instant;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
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
    }

    /** the time when the freeze starts */
    private volatile Instant freezeTime;

    /** the last freezeTime based on which the nodes were frozen */
    private volatile Instant lastFrozenTime;

    public DualStateImpl() {}

    protected DualStateImpl(final DualStateImpl that) {
        super(that);
        this.freezeTime = that.freezeTime;
        this.lastFrozenTime = that.lastFrozenTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeInstant(freezeTime);
        out.writeInstant(lastFrozenTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        freezeTime = in.readInstant();
        lastFrozenTime = in.readInstant();
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
        return ClassVersion.ORIGINAL;
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DualStateImpl that = (DualStateImpl) o;

        return new EqualsBuilder()
                .append(freezeTime, that.freezeTime)
                .append(lastFrozenTime, that.lastFrozenTime)
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(freezeTime)
                .append(lastFrozenTime)
                .toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
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
