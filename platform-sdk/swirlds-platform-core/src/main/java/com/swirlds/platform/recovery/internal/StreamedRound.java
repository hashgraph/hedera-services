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

package com.swirlds.platform.recovery.internal;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.common.event.ConsensusEvent;
import com.swirlds.common.event.PlatformEvent;
import com.swirlds.common.iterator.TypedIterator;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.CesEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * An implementation of a {@link Round} used by streaming classes.
 */
public class StreamedRound implements Round {

    private final List<CesEvent> events;
    private final long roundNumber;
    private final Instant consensusTimestamp;
    private final Roster consensusRoster;

    public StreamedRound(
            @NonNull final Roster consensusRoster, @NonNull final List<CesEvent> events, final long roundNumber) {
        this.events = events;
        this.roundNumber = roundNumber;
        events.stream().map(CesEvent::getPlatformEvent).forEach(PlatformEvent::setConsensusTimestampsOnTransactions);
        consensusTimestamp = events.get(events.size() - 1).getPlatformEvent().getConsensusTimestamp();
        this.consensusRoster = Objects.requireNonNull(consensusRoster);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Iterator<ConsensusEvent> iterator() {
        return new TypedIterator<>(events.iterator());
    }

    public @NonNull List<CesEvent> getEvents() {
        return events;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRoundNum() {
        return roundNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return events.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getEventCount() {
        return events.size();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Roster getConsensusRoster() {
        return consensusRoster;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }
}
