/*
 * Copyright (C) 2021-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.consensus;

import static com.swirlds.platform.system.status.PlatformStatus.REPLAYING_EVENTS;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.ConsensusImpl;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.linking.ConsensusLinker;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.AddedEventMetrics;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.metrics.ConsensusMetricsImpl;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * The default implementation of the {@link ConsensusEngine} interface
 */
public class DefaultConsensusEngine implements ConsensusEngine {

    /**
     * Stores non-ancient events and manages linking and unlinking.
     */
    private final InOrderLinker linker;

    /**
     * Executes the hashgraph consensus algorithm.
     */
    private final Consensus consensus;

    private final AncientMode ancientMode;
    private final int roundsNonAncient;

    private final AddedEventMetrics eventAddedMetrics;

    /**
     * Constructor
     *
     * @param platformContext the platform context
     * @param roster     the current roster
     * @param selfId          the ID of the node
     */
    public DefaultConsensusEngine(
            @NonNull final PlatformContext platformContext,
            @NonNull final Roster roster,
            @NonNull final NodeId selfId) {

        final ConsensusMetrics consensusMetrics = new ConsensusMetricsImpl(selfId, platformContext.getMetrics());
        consensus = new ConsensusImpl(platformContext, consensusMetrics, roster);

        linker = new ConsensusLinker(platformContext, selfId);
        ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();
        roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();

        eventAddedMetrics = new AddedEventMetrics(selfId, platformContext.getMetrics());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updatePlatformStatus(@NonNull final PlatformStatus platformStatus) {
        consensus.setPcesMode(platformStatus == REPLAYING_EVENTS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<ConsensusRound> addEvent(@NonNull final PlatformEvent event) {
        Objects.requireNonNull(event);

        final EventImpl linkedEvent = linker.linkEvent(event);
        if (linkedEvent == null) {
            // linker discarded an ancient event
            return List.of();
        }

        List<Bytes> transactions = event.getGossipEvent().transactions();
        boolean isNewFormat = !transactions.isEmpty();
        final List<ConsensusRound> consensusRounds = consensus.addEvent(linkedEvent);
        eventAddedMetrics.eventAdded(linkedEvent, isNewFormat);

        if (!consensusRounds.isEmpty()) {
            // If multiple rounds reach consensus at the same moment there is no need to pass in
            // each event window. The latest event window is sufficient to keep event storage clean.
            linker.setEventWindow(consensusRounds.getLast().getEventWindow());
        }

        return consensusRounds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void outOfBandSnapshotUpdate(@NonNull final ConsensusSnapshot snapshot) {
        final long ancientThreshold = snapshot.getMinimumGenerationNonAncient(roundsNonAncient);
        final EventWindow eventWindow =
                new EventWindow(snapshot.round(), ancientThreshold, ancientThreshold, ancientMode);

        linker.clear();
        linker.setEventWindow(eventWindow);
        consensus.loadSnapshot(snapshot);
    }
}
