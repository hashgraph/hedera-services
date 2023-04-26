/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.events.ConsensusData;
import com.swirlds.platform.event.InternalEventData;
import com.swirlds.platform.internal.EventImpl;
import org.apache.commons.lang3.builder.EqualsBuilder;

/**
 * An event with the same behavior as a standard event but with the addition of some debugging metadata.
 *
 * All testing and debugging utilities should use IndexedEvent instead of EventImpl directly.
 */
public class IndexedEvent extends EventImpl {

    private long generatorIndex;
    private int sequenceNum = -1;

    private static final long CLASS_ID = 0x284d35dc6f9265d0L;

    public IndexedEvent() {}

    public IndexedEvent(
            final BaseEventHashedData baseEventHashedData, final BaseEventUnhashedData baseEventUnhashedData) {
        super(baseEventHashedData, baseEventUnhashedData);
    }

    public IndexedEvent(
            final BaseEventHashedData baseEventHashedData,
            final BaseEventUnhashedData baseEventUnhashedData,
            final ConsensusData consensusData) {
        super(baseEventHashedData, baseEventUnhashedData, consensusData);
    }

    public IndexedEvent(
            final BaseEventHashedData baseEventHashedData,
            final BaseEventUnhashedData baseEventUnhashedData,
            final EventImpl selfParent,
            final EventImpl otherParent) {
        super(baseEventHashedData, baseEventUnhashedData, selfParent, otherParent);
    }

    public IndexedEvent(final EventImpl event) {
        super(
                event.getBaseEventHashedData(),
                event.getBaseEventUnhashedData(),
                event.getConsensusData(),
                event.getSelfParent(),
                event.getOtherParent());
    }

    /**
     * Convert this object into a regular EventImpl (as opposed to the IndexedEvent subclass).
     */
    public EventImpl convertToEventImpl() {
        final EventImpl event = new EventImpl(
                getBaseEventHashedData(),
                getBaseEventUnhashedData(),
                getConsensusData(),
                getSelfParent(),
                getOtherParent());
        CryptographyHolder.get().digestSync(event);
        return event;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * Get the index of this event with respect to the generator that created it.
     */
    public long getGeneratorIndex() {
        return generatorIndex;
    }

    /**
     * Set the generator index of this event.
     */
    public void setGeneratorIndex(final long generatorIndex) {
        this.generatorIndex = generatorIndex;
    }

    public int getSequenceNum() {
        return sequenceNum;
    }

    public void setSequenceNum(final int sequenceNum) {
        this.sequenceNum = sequenceNum;
    }

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof IndexedEvent)) {
            return false;
        }
        if (this == o) {
            return true;
        }

        final InternalEventData data = getInternalEventData();
        final InternalEventData otherData = ((IndexedEvent) o).getInternalEventData();

        return super.equals(o)
                && new EqualsBuilder()
                        .append(data.isCleared(), otherData.isCleared())
                        .append(data.isFameDecided(), otherData.isFameDecided())
                        .append(data.isConsensus(), otherData.isConsensus())
                        .append(data.hasUserTransactions(), otherData.hasUserTransactions())
                        .append(data.getRecTimes(), otherData.getRecTimes())
                        .isEquals();
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
