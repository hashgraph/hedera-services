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

package com.swirlds.platform.event.stale;

import static com.swirlds.platform.event.AncientMode.BIRTH_ROUND_THRESHOLD;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.wiring.transformers.RoutableData;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.sequence.map.StandardSequenceMap;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;

/**
 * Detects when a self event becomes stale. Note that this detection may not observe a self event go stale if the node
 * needs to reconnect or restart.
 */
public class DefaultStaleEventDetector implements StaleEventDetector {

    /**
     * The ID of this node.
     */
    private final NodeId selfId;

    /**
     * Self events that have not yet reached consensus.
     */
    private final StandardSequenceMap<EventDescriptorWrapper, PlatformEvent> selfEvents;

    /**
     * The most recent event window we know about.
     */
    private EventWindow currentEventWindow;

    /**
     * Metrics for the stale event detector.
     */
    private final StaleEventDetectorMetrics metrics;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param selfId          the ID of this node
     */
    public DefaultStaleEventDetector(@NonNull final PlatformContext platformContext, @NonNull final NodeId selfId) {

        this.selfId = Objects.requireNonNull(selfId);

        final AncientMode ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();

        final ToLongFunction<EventDescriptorWrapper> getAncientIdentifier;
        if (ancientMode == BIRTH_ROUND_THRESHOLD) {
            getAncientIdentifier = ed -> ed.eventDescriptor().birthRound();
        } else {
            getAncientIdentifier = ed -> ed.eventDescriptor().generation();
        }
        selfEvents = new StandardSequenceMap<>(0, 1024, true, getAncientIdentifier);

        metrics = new StaleEventDetectorMetrics(platformContext);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<RoutableData<StaleEventDetectorOutput>> addSelfEvent(@NonNull final PlatformEvent event) {
        if (currentEventWindow == null) {
            throw new IllegalStateException("Event window must be set before adding self events");
        }

        final RoutableData<StaleEventDetectorOutput> selfEvent =
                new RoutableData<>(StaleEventDetectorOutput.SELF_EVENT, event);

        if (currentEventWindow.isAncient(event)) {
            // Although unlikely, it is plausible for an event to go stale before it is added to the detector.
            handleStaleEvent(event);

            final RoutableData<StaleEventDetectorOutput> staleEvent =
                    new RoutableData<>(StaleEventDetectorOutput.STALE_SELF_EVENT, event);
            return List.of(selfEvent, staleEvent);
        }

        selfEvents.put(event.getDescriptor(), event);
        return List.of(selfEvent);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<RoutableData<StaleEventDetectorOutput>> addConsensusRound(
            @NonNull final ConsensusRound consensusRound) {
        for (final PlatformEvent event : consensusRound.getConsensusEvents()) {
            if (event.getCreatorId().equals(selfId)) {
                selfEvents.remove(event.getDescriptor());
            }
        }

        final List<PlatformEvent> staleEvents = new ArrayList<>();
        currentEventWindow = consensusRound.getEventWindow();
        selfEvents.shiftWindow(currentEventWindow.getAncientThreshold(), (descriptor, event) -> staleEvents.add(event));

        final List<RoutableData<StaleEventDetectorOutput>> output = new ArrayList<>(staleEvents.size());
        for (final PlatformEvent event : staleEvents) {
            handleStaleEvent(event);
            output.add(new RoutableData<>(StaleEventDetectorOutput.STALE_SELF_EVENT, event));
        }

        return output;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInitialEventWindow(@NonNull final EventWindow initialEventWindow) {
        this.currentEventWindow = Objects.requireNonNull(initialEventWindow);
        selfEvents.shiftWindow(currentEventWindow.getAncientThreshold());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        selfEvents.clear();
        currentEventWindow = null;
    }

    /**
     * Handle a stale event.
     *
     * @param event the stale event
     */
    private void handleStaleEvent(@NonNull final PlatformEvent event) {
        metrics.reportStaleEvent(event);
    }
}
