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

package com.swirlds.platform.components;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.ConsensusEventStorage;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.wiring.ClearTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The default implementation of the {@link ConsensusEngine} interface
 */
public class DefaultConsensusEngine implements ConsensusEngine {

    /**
     * Stores non-ancient events and manages linking and unlinking.
     */
    private final ConsensusEventStorage eventStorage;

    /**
     * A functor that provides access to a {@code Consensus} instance.
     */
    private final Supplier<Consensus> consensusSupplier;

    /**
     * Constructor
     *
     * @param platformContext   the platform context
     * @param selfId            the ID of the node
     * @param consensusSupplier provides the current consensus instance
     */
    public DefaultConsensusEngine(
            @NonNull final PlatformContext platformContext,
            @NonNull final NodeId selfId,
            @NonNull final Supplier<Consensus> consensusSupplier) {

        eventStorage = new ConsensusEventStorage(platformContext, selfId);
        this.consensusSupplier = Objects.requireNonNull(consensusSupplier);

        // TODO don't do it this way
        //  Needs to be updated at genesis and at reconnect
        eventStorage.setNonAncientEventWindow(
                NonAncientEventWindow.getGenesisNonAncientEventWindow(AncientMode.GENERATION_THRESHOLD));
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

        final List<ConsensusRound> consensusRounds = consensusSupplier.get().addEvent(event);

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
    public void clear(@NonNull final ClearTrigger ignored) {
        eventStorage.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInitialEventWindow(@NonNull final NonAncientEventWindow window) {
        eventStorage.setNonAncientEventWindow(window);
    } // TODO use this
}
