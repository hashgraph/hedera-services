/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event;

import static com.swirlds.platform.consensus.ConsensusConstants.ROUND_FIRST;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.eventhandling.EventConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Buffers events from the future (i.e. events with a birth round that is greater than the round that consensus is
 * currently working on). It is important to note that the future event buffer is only used to store events from the
 * near future that can be fully validated. Events from beyond the event horizon (i.e. far future events that cannot be
 * immediately validated) are never stored by any part of the system.
 * <p>
 * Output from the future event buffer is guaranteed to preserve topological ordering, as long as the input to the
 * buffer is topologically ordered.
 */
public class FutureEventBuffer {

    /**
     * A little lambda that builds a new array list. Cache this here so we don't have to create a new lambda each time
     * we buffer a future event.
     */
    private static final Function<Long, List<GossipEvent>> BUILD_LIST = x -> new ArrayList<>();

    private NonAncientEventWindow eventWindow;

    private final SequenceMap<Long /* birth round */, List<GossipEvent>> futureEvents =
            new StandardSequenceMap<>(ROUND_FIRST, 8, true, x -> x);

    private final AtomicLong bufferedEventCount = new AtomicLong(0);

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     */
    public FutureEventBuffer(@NonNull final PlatformContext platformContext) {
        final AncientMode ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();

        eventWindow = NonAncientEventWindow.getGenesisNonAncientEventWindow(ancientMode);

        platformContext
                .getMetrics()
                .getOrCreate(
                        new FunctionGauge.Config<>("platform", "futureEventBuffer", Long.class, bufferedEventCount::get)
                                .withDescription("the number of events sitting in the future event buffer")
                                .withUnit("count"));
    }

    /**
     * Add an event to the future event buffer.
     *
     * @param event the event to add
     * @return a list containing the event if it is not a time traveler, or null if the event is from the future and
     * needs to be buffered.
     */
    @Nullable
    public List<GossipEvent> addEvent(@NonNull final GossipEvent event) {
        if (eventWindow.isAncient(event)) {
            // we can safely ignore ancient events
            return null;
        } else if (event.getHashedData().getBirthRound() <= eventWindow.getPendingConsensusRound()) {
            // this is not a future event, no need to buffer it
            return List.of(event);
        }

        // this is a future event, buffer it
        futureEvents
                .computeIfAbsent(event.getHashedData().getBirthRound(), BUILD_LIST)
                .add(event);
        bufferedEventCount.incrementAndGet();
        return null;
    }

    /**
     * Update the current event window. As the event window advances, time catches up to time travelers, and events that
     * were previously from the future are now from the present.
     *
     * @param eventWindow the new event window
     * @return a list of events that were previously from the future but are now from the present
     */
    public List<GossipEvent> updateEventWindow(@NonNull final NonAncientEventWindow eventWindow) {
        this.eventWindow = Objects.requireNonNull(eventWindow);

        final List<GossipEvent> events = new ArrayList<>();
        futureEvents.shiftWindow(eventWindow.getPendingConsensusRound(), (round, roundEvents) -> {
            for (final GossipEvent event : roundEvents) {
                if (!eventWindow.isAncient(event)) {
                    events.add(event);
                }
            }
        });

        bufferedEventCount.addAndGet(-events.size());
        return events;
    }

    /**
     * Clear all data from the future event buffer.
     */
    public void clear() {
        futureEvents.clear();
    }
}
