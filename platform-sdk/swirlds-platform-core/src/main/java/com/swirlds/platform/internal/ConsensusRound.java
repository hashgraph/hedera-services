/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.system.Round;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.util.iterator.TypedIterator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * A consensus round with all its events.
 */
public class ConsensusRound implements Round {

    /** an unmodifiable list of consensus events in this round, in consensus order */
    private final List<EventImpl> consensusEvents;

    /** the consensus generations when this round reached consensus */
    private final GraphGenerations generations;

    /** this round's number */
    private final long roundNum;

    /** the last event in the round */
    private EventImpl lastEvent;

    /** The number of application transactions in this round */
    private int numAppTransactions = 0;

    /**
     * The event that, when added to the hashgraph, caused this round to reach consensus.
     */
    private final EventImpl keystoneEvent;

    /**
     * Create a new instance with the provided consensus events.
     *
     * @param consensusEvents the events in the round, in consensus order
     * @param keystoneEvent   the event that, when added to the hashgraph, caused this round to reach consensus
     * @param generations     the consensus generations for this round
     */
    public ConsensusRound(
            @NonNull final List<EventImpl> consensusEvents,
            @NonNull final EventImpl keystoneEvent,
            @NonNull final GraphGenerations generations) {

        throwArgNull(consensusEvents, "consensusEvents");
        throwArgNull(keystoneEvent, "keystoneEvent");
        throwArgNull(generations, "generations");

        this.consensusEvents = Collections.unmodifiableList(consensusEvents);
        this.keystoneEvent = keystoneEvent;
        this.generations = generations;

        for (final EventImpl e : consensusEvents) {
            numAppTransactions += e.getNumAppTransactions();
        }

        final EventImpl lastInList = consensusEvents.get(consensusEvents.size() - 1);
        if (lastInList.isLastInRoundReceived()) {
            lastEvent = lastInList;
        }

        this.roundNum = consensusEvents.get(0).getRoundReceived();
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
    public List<EventImpl> getConsensusEvents() {
        return consensusEvents;
    }

    /**
     * @return the consensus generations when this round reached consensus
     */
    public GraphGenerations getGenerations() {
        return generations;
    }

    /**
     * @return the number of events in this round
     */
    public int getNumEvents() {
        return consensusEvents.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<ConsensusEvent> iterator() {
        return new TypedIterator<>(consensusEvents.iterator());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRoundNum() {
        return roundNum;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return consensusEvents.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getEventCount() {
        return consensusEvents.size();
    }

    /**
     * @return the last event of this round, or null if this round is not complete
     */
    public EventImpl getLastEvent() {
        return lastEvent;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ConsensusRound round = (ConsensusRound) o;

        return new EqualsBuilder()
                .append(consensusEvents, round.consensusEvents)
                .isEquals();
    }

    /**
     * @return the event that, when added to the hashgraph, caused this round to reach consensus
     */
    public @NonNull EventImpl getKeystoneEvent() {
        return keystoneEvent;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(consensusEvents).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .append("round", roundNum)
                .append("consensus events", EventUtils.toShortStrings(consensusEvents))
                .toString();
    }
}
