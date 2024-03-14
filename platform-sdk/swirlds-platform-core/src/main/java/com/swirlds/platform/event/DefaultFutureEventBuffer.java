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
import com.swirlds.platform.wiring.ClearTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Default implementation of the {@link FutureEventBuffer}
 */
public class DefaultFutureEventBuffer implements FutureEventBuffer {

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
    public DefaultFutureEventBuffer(@NonNull final PlatformContext platformContext) {
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
     * {@inheritDoc}
     */
    @Override
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
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public List<GossipEvent> updateEventWindow(@NonNull final NonAncientEventWindow eventWindow) {
        this.eventWindow = Objects.requireNonNull(eventWindow);

        // We want to release all events with birth rounds less than or equal to the pending consensus round.
        // In order to do that, we tell the sequence map to shift its window to the oldest round that we want
        // to keep within the buffer.
        final long oldestRoundToBuffer = eventWindow.getPendingConsensusRound() + 1;

        final List<GossipEvent> events = new ArrayList<>();
        futureEvents.shiftWindow(oldestRoundToBuffer, (round, roundEvents) -> {
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
     * {@inheritDoc}
     */
    @Override
    public void clear(@NonNull final ClearTrigger clearTrigger) {
        futureEvents.clear();
    }
}
