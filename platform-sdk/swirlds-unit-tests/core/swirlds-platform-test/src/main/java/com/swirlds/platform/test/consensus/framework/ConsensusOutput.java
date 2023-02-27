/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.swirlds.platform.test.consensus.framework;

import com.swirlds.common.time.Time;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.observers.EventAddedObserver;
import com.swirlds.platform.observers.StaleEventObserver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores all output of consensus used in testing. This output can be used to validate consensus
 * results.
 */
public class ConsensusOutput
        implements EventAddedObserver, ConsensusRoundObserver, StaleEventObserver, Clearable {
    private final Time time;
    private final LinkedList<ConsensusRound> consensusRounds;
    private final LinkedList<EventImpl> addedEvents;
    private final LinkedList<EventImpl> staleEvents;

    public ConsensusOutput(final Time time) {
        this.time = time;
        addedEvents = new LinkedList<>();
        consensusRounds = new LinkedList<>();
        staleEvents = new LinkedList<>();
    }

    @Override
    public void eventAdded(final EventImpl event) {
        addedEvents.add(event);
    }

    @Override
    public void consensusRound(final ConsensusRound consensusRound) {
        for (final EventImpl event : consensusRound.getConsensusEvents()) {
            // this a workaround until Consensus starts using a clock that is provided
            event.setReachedConsTimestamp(time.now());
        }
        consensusRounds.add(consensusRound);
    }

    @Override
    public void staleEvent(final EventImpl event) {
        staleEvents.add(event);
    }

    /**
     * @return a queue of all events that have been marked as stale
     */
    public LinkedList<EventImpl> getStaleEvents() {
        return staleEvents;
    }

    /**
     * @return a queue of all rounds that have reached consensus
     */
    public LinkedList<ConsensusRound> getConsensusRounds() {
        return consensusRounds;
    }

    public LinkedList<EventImpl> getAddedEvents() {
        return addedEvents;
    }

    public List<EventImpl> sortedAddedEvents() {
        final List<EventImpl> sortedEvents = new ArrayList<>(addedEvents);
        sortedEvents.sort(
                Comparator.comparingLong(EventImpl::getGeneration)
                        .thenComparingLong(EventImpl::getCreatorId)
                        .thenComparing(EventImpl::getBaseHash));
        return sortedEvents;
    }

    @Override
    public void clear() {
        addedEvents.clear();
        consensusRounds.clear();
        staleEvents.clear();
    }
}
