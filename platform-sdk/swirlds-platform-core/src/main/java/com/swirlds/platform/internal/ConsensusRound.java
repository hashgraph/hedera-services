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

package com.swirlds.platform.internal;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.util.iterator.TypedIterator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** A consensus round with events and all other relevant data. */
public class ConsensusRound implements Round {
    /** an unmodifiable list of consensus events in this round, in consensus order */
    private final List<EventImpl> consensusEvents;
    /** the consensus generations when this round reached consensus */
    private final GraphGenerations generations;
    /** the non-ancient event window for this round */
    private final NonAncientEventWindow nonAncientEventWindow;
    /** The number of application transactions in this round */
    private int numAppTransactions = 0;
    /** A snapshot of consensus at this consensus round */
    private final ConsensusSnapshot snapshot;
    /** The event that, when added to the hashgraph, caused this round to reach consensus. */
    private final EventImpl keystoneEvent;

    /**
     * The consensus roster for this round.
     */
    private final AddressBook consensusRoster;

    /**
     * Create a new instance with the provided consensus events.
     *
     * @param consensusRoster       the consensus roster for this round
     * @param consensusEvents       the events in the round, in consensus order
     * @param keystoneEvent         the event that, when added to the hashgraph, caused this round to reach consensus
     * @param generations           the consensus generations for this round
     * @param nonAncientEventWindow the non-ancient event window for this round
     * @param snapshot              snapshot of consensus at this round
     */
    public ConsensusRound(
            @NonNull final AddressBook consensusRoster,
            @NonNull final List<EventImpl> consensusEvents,
            @NonNull final EventImpl keystoneEvent,
            @NonNull final GraphGenerations generations,
            @NonNull final NonAncientEventWindow nonAncientEventWindow,
            @NonNull final ConsensusSnapshot snapshot) {

        this.consensusRoster = Objects.requireNonNull(consensusRoster);
        this.consensusEvents = Collections.unmodifiableList(Objects.requireNonNull(consensusEvents));
        this.keystoneEvent = Objects.requireNonNull(keystoneEvent);
        this.generations = Objects.requireNonNull(generations);
        this.nonAncientEventWindow = Objects.requireNonNull(nonAncientEventWindow);
        this.snapshot = Objects.requireNonNull(snapshot);

        for (final EventImpl e : consensusEvents) {
            numAppTransactions += e.getNumAppTransactions();
        }
    }

    /**
     * Returns the number of application transactions in this round
     *
     * @return the number of application transactions
     */
    public int getNumAppTransactions() {
        return numAppTransactions;
    }

    /**
     * Provides an unmodifiable list of the consensus event in this round.
     *
     * @return the list of events in this round
     */
    public @NonNull List<EventImpl> getConsensusEvents() {
        return consensusEvents;
    }

    /**
     * @return the consensus generations when this round reached consensus
     */
    public @NonNull GraphGenerations getGenerations() {
        return generations;
    }

    /**
     * @return the non-ancient event window for this round
     */
    public @NonNull NonAncientEventWindow getNonAncientEventWindow() {
        return nonAncientEventWindow;
    }

    /**
     * @return a snapshot of consensus at this consensus round
     */
    public @NonNull ConsensusSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * @return the number of events in this round
     */
    public int getNumEvents() {
        return consensusEvents.size();
    }

    /** {@inheritDoc} */
    @Override
    public @NonNull Iterator<ConsensusEvent> iterator() {
        return new TypedIterator<>(consensusEvents.iterator());
    }

    /** {@inheritDoc} */
    @Override
    public long getRoundNum() {
        return snapshot.round();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() {
        return consensusEvents.isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public int getEventCount() {
        return consensusEvents.size();
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public AddressBook getConsensusRoster() {
        return consensusRoster;
    }

    public @NonNull Instant getConsensusTimestamp() {
        return snapshot.consensusTimestamp();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final ConsensusRound that = (ConsensusRound) other;
        return Objects.equals(consensusEvents, that.consensusEvents);
    }

    /**
     * @return the event that, when added to the hashgraph, caused this round to reach consensus
     */
    public @NonNull EventImpl getKeystoneEvent() {
        return keystoneEvent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(consensusEvents);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("round", snapshot.round())
                .append("consensus events", EventUtils.toShortStrings(consensusEvents))
                .toString();
    }
}
