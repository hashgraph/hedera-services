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

package com.swirlds.platform.recovery.internal;

import com.swirlds.common.io.IOIterator;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.CesEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Takes an iterator that walks over events and returns an iterator that walks over rounds.
 */
public class EventStreamRoundIterator implements IOIterator<StreamedRound> {

    private final IOIterator<CesEvent> eventIterator;
    private final boolean allowPartialRound;
    private final AddressBook consensusRoster;

    private StreamedRound next;
    private boolean ended = false;

    /**
     * Create a new iterator that walks over rounds.
     *
     * @param consensusRoster      the consensus roster
     * @param eventStreamDirectory a directory containing event stream files
     * @param startingRound        the round to start iterating at, or
     *                             {@link EventStreamPathIterator#FIRST_ROUND_AVAILABLE} if all available rounds should
     *                             be returned.
     * @param allowPartialRound    if true then allow the last round to contain just some of the events from that round.
     *                             If false then do not return a round that does not have all of its events.
     */
    public EventStreamRoundIterator(
            @NonNull final AddressBook consensusRoster,
            final Path eventStreamDirectory,
            final long startingRound,
            boolean allowPartialRound)
            throws IOException {
        this(
                consensusRoster,
                new EventStreamMultiFileIterator(eventStreamDirectory, new EventStreamRoundLowerBound(startingRound)),
                allowPartialRound);
    }

    /**
     * Create a new iterator that walks over rounds.
     *
     * @param consensusRoster the consensus roster
     * @param eventIterator   an iterator that walks over events
     */
    public EventStreamRoundIterator(
            @NonNull final AddressBook consensusRoster,
            final IOIterator<CesEvent> eventIterator,
            boolean allowPartialRound) {
        this.consensusRoster = Objects.requireNonNull(consensusRoster);
        this.eventIterator = Objects.requireNonNull(eventIterator);
        this.allowPartialRound = allowPartialRound;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() throws IOException {

        if (ended) {
            // Once we decide that we have finished (e.g. if a round is missing events or a file is corrupted),
            // under no circumstances should we continue to return events, even if there
            // are more in the data stream.
            return false;
        }

        if (next != null) {
            return true;
        }

        if (!eventIterator.hasNext()) {
            return false;
        }

        final List<CesEvent> events = new ArrayList<>();

        final long round = eventIterator.peek().getRoundReceived();
        while (eventIterator.hasNext() && eventIterator.peek().getRoundReceived() == round) {
            events.add(eventIterator.next());
        }

        if (!allowPartialRound) {
            final CesEvent lastEvent = events.get(events.size() - 1);
            if (!lastEvent.isLastInRoundReceived()) {
                ended = true;
                return false;
            }
        }

        next = new StreamedRound(consensusRoster, events, round);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        eventIterator.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StreamedRound peek() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return next;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StreamedRound next() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        try {
            return next;
        } finally {
            next = null;
        }
    }
}
