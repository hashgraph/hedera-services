// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.recovery.internal;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.CesEvent;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.util.iterator.TypedIterator;
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
