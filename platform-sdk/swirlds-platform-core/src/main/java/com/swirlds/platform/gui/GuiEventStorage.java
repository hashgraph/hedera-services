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

package com.swirlds.platform.gui;

import static com.swirlds.platform.event.AncientMode.GENERATION_THRESHOLD;
import static com.swirlds.platform.system.events.EventConstants.FIRST_GENERATION;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.ConsensusImpl;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.NoOpConsensusMetrics;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * This class is responsible for storing events utilized by the GUI.
 */
public class GuiEventStorage {

    // A note on concurrency: although all input to this class is sequential and thread safe, access to this class
    // happens asynchronously. This requires all methods to be synchronized.

    private long maxGeneration = FIRST_GENERATION;

    private final Consensus consensus;
    private final SimpleLinker linker;
    private final Configuration configuration;

    /**
     * Constructor
     *
     * @param configuration this node's configuration
     * @param addressBook   the network's address book
     */
    public GuiEventStorage(@NonNull final Configuration configuration, @NonNull final AddressBook addressBook) {

        this.configuration = Objects.requireNonNull(configuration);
        final PlatformContext platformContext = new DefaultPlatformContext(
                configuration, new NoOpMetrics(), CryptographyHolder.get(), Time.getCurrent());

        this.consensus = new ConsensusImpl(platformContext, new NoOpConsensusMetrics(), addressBook);
        // Future work: birth round compatibility for GUI
        this.linker = new SimpleLinker(GENERATION_THRESHOLD);
    }

    /**
     * Get the consensus object. This is a local copy, not one used by an active platform.
     */
    @NonNull
    public Consensus getConsensus() {
        return consensus;
    }

    /**
     * Handle a preconsensus event. Called after events are released from the orphan buffer.
     *
     * @param event the event to handle
     */
    public synchronized void handlePreconsensusEvent(@NonNull final GossipEvent event) {
        maxGeneration = Math.max(maxGeneration, event.getGeneration());

        final EventImpl eventImpl = linker.linkEvent(event);
        if (eventImpl == null) {
            return;
        }

        final List<ConsensusRound> rounds = consensus.addEvent(eventImpl);

        if (rounds == null) {
            return;
        }

        linker.setNonAncientThreshold(
                rounds.getLast().getNonAncientEventWindow().getAncientThreshold());
    }

    /**
     * Handle a consensus snapshot override (i.e. what happens when we start from a node state at restart/reconnect
     * boundaries).
     *
     * @param snapshot the snapshot to handle
     */
    public synchronized void handleSnapshotOverride(@NonNull final ConsensusSnapshot snapshot) {
        consensus.loadSnapshot(snapshot);
        linker.clear();
        linker.setNonAncientThreshold(snapshot.getMinimumGenerationNonAncient(
                configuration.getConfigData(ConsensusConfig.class).roundsNonAncient()));
    }

    /**
     * Get the maximum generation of any event in the hashgraph.
     *
     * @return the maximum generation of any event in the hashgraph
     */
    public synchronized long getMaxGeneration() {
        return maxGeneration;
    }

    /**
     * Get a list of all non-ancient events in the hashgraph.
     */
    @NonNull
    public synchronized List<EventImpl> getNonAncientEvents() {
        return linker.getNonAncientEvents();
    }
}
