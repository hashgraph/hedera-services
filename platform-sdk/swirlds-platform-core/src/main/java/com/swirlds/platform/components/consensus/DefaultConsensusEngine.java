/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
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
    private final ConsensusEventStorage eventStorage;

    /**
     * Executes the hashgraph consensus algorithm.
     */
    private final Consensus consensus;

    private final AncientMode ancientMode;
    private final int roundsNonAncient;

    /**
     * Constructor
     *
     * @param platformContext the platform context
     * @param selfId          the ID of the node
     * @param consensus       executes the hashgraph consensus algorithm
     */
    public DefaultConsensusEngine(
            @NonNull final PlatformContext platformContext,
            @NonNull final NodeId selfId,
            @NonNull final Consensus consensus) {

        this.consensus = Objects.requireNonNull(consensus);
        eventStorage = new ConsensusEventStorage(platformContext, selfId);
        ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();
        roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<ConsensusRound> addEvent(@NonNull final EventImpl eventWrapper) {
        Objects.requireNonNull(eventWrapper);

        // Intentionally ignore the EventImpl wrapper passed into this method. As a follow
        // up task, the input type of this method will be changed to GossipEvent.
        final GossipEvent gossipEvent = eventWrapper.getBaseEvent();
        final EventImpl event = eventStorage.linkEvent(gossipEvent);

        if (event == null) {
            // event storage discarded an ancient event
            return List.of();
        }

        final List<ConsensusRound> consensusRounds = consensus.addEvent(event);

        if (!consensusRounds.isEmpty()) {
            // If multiple rounds reach consensus at the same moment there is no need to pass in
            // each event window. The latest event window is sufficient to keep event storage clean.
            eventStorage.setNonAncientEventWindow(consensusRounds.getLast().getNonAncientEventWindow());
        }

        return consensusRounds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void outOfBandSnapshotUpdate(@NonNull final ConsensusSnapshot snapshot) {
        final long ancientThreshold = snapshot.getMinimumGenerationNonAncient(roundsNonAncient);
        final NonAncientEventWindow nonAncientEventWindow =
                new NonAncientEventWindow(snapshot.round(), ancientThreshold, ancientThreshold, ancientMode);

        eventStorage.clear();
        eventStorage.setNonAncientEventWindow(nonAncientEventWindow);
        consensus.loadSnapshot(snapshot);
    }
}
