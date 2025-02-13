/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import com.swirlds.base.time.Time;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.sequence.set.SequenceSet;
import com.swirlds.platform.sequence.set.StandardSequenceSet;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores all output of consensus used in testing. This output can be used to validate consensus results.
 */
public class ConsensusOutput implements Clearable {
    private final Time time;
    private final LinkedList<ConsensusRound> consensusRounds;
    private final LinkedList<PlatformEvent> addedEvents;
    private final LinkedList<PlatformEvent> staleEvents;

    private final SequenceSet<PlatformEvent> nonAncientEvents;
    private final SequenceSet<EventDescriptorWrapper> nonAncientConsensusEvents;

    private long latestRound;

    private EventWindow eventWindow = EventWindow.getGenesisEventWindow(AncientMode.GENERATION_THRESHOLD);

    /**
     * Creates a new instance.
     *
     * @param time the time to use for marking events
     */
    public ConsensusOutput(@NonNull final Time time) {
        this.time = time;
        addedEvents = new LinkedList<>();
        consensusRounds = new LinkedList<>();
        staleEvents = new LinkedList<>();

        // FUTURE WORK: birth round compatibility
        nonAncientEvents = new StandardSequenceSet<>(0, 1024, true, PlatformEvent::getGeneration);
        nonAncientConsensusEvents = new StandardSequenceSet<>(
                0, 1024, true, ed -> ed.eventDescriptor().generation());
    }

    public void eventAdded(@NonNull final PlatformEvent event) {
        addedEvents.add(event);
        nonAncientEvents.add(event);
    }

    public void consensusRound(@NonNull final ConsensusRound consensusRound) {
        consensusRounds.add(consensusRound);

        // Look for stale events
        for (final PlatformEvent consensusEvent : consensusRound.getConsensusEvents()) {
            nonAncientConsensusEvents.add(consensusEvent.getDescriptor());
        }
        final long ancientThreshold = consensusRound.getEventWindow().getAncientThreshold();
        nonAncientEvents.shiftWindow(ancientThreshold, e -> {
            if (!nonAncientConsensusEvents.contains(e.getDescriptor())) {
                staleEvents.add(e);
            }
        });
        nonAncientConsensusEvents.shiftWindow(ancientThreshold);

        eventWindow = consensusRound.getEventWindow();
    }

    public void setConsensusRounds(@NonNull final List<ConsensusRound> rounds) {
        consensusRounds.addAll(rounds);
    }

    /**
     * @return a queue of all events that have been marked as stale
     */
    public @NonNull LinkedList<PlatformEvent> getStaleEvents() {
        return staleEvents;
    }

    /**
     * @return a queue of all rounds that have reached consensus
     */
    public @NonNull LinkedList<ConsensusRound> getConsensusRounds() {
        return consensusRounds;
    }

    public @NonNull LinkedList<PlatformEvent> getAddedEvents() {
        return addedEvents;
    }

    public @NonNull List<PlatformEvent> sortedAddedEvents() {
        final List<PlatformEvent> sortedEvents = new ArrayList<>(addedEvents);
        sortedEvents.sort(Comparator.comparingLong(PlatformEvent::getGeneration)
                .thenComparingLong(e -> e.getCreatorId().id())
                .thenComparing(PlatformEvent::getHash));
        return sortedEvents;
    }

    /**
     * Get the latest round that reached consensus.
     *
     * @return the latest round that reached consensus
     */
    public long getLatestRound() {
        return latestRound;
    }

    /**
     * Get the current event window.
     * @return the current event window
     */
    @NonNull
    public EventWindow getEventWindow() {
        return eventWindow;
    }

    @Override
    public void clear() {
        addedEvents.clear();
        consensusRounds.clear();
        staleEvents.clear();
        nonAncientEvents.clear();
        nonAncientConsensusEvents.clear();
        latestRound = 0;
        eventWindow = EventWindow.getGenesisEventWindow(AncientMode.GENERATION_THRESHOLD);
    }
}
