/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.sync.turbo;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.gossip.shadowgraph.LatestEventTipsetTracker;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.sync.protocol.PeerAgnosticSyncChecks;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A variation of a sync protocol that continuously performs multiple concurrent syncs.
 */
public class TurboSyncProtocol implements Protocol {

    private static final Logger logger = LogManager.getLogger(TurboSyncProtocol.class);

    private final PlatformContext platformContext;
    private final AddressBook addressBook;
    private final NodeId selfId;
    private final FallenBehindManager fallenBehindManager;
    private final PeerAgnosticSyncChecks peerAgnosticSyncChecks;
    private final ParallelExecutor executor;
    private final ShadowGraph shadowgraph;
    private final Supplier<GraphGenerations> generationsSupplier;
    private final LatestEventTipsetTracker latestEventTipsetTracker;
    private final InterruptableConsumer<GossipEvent> gossipEventConsumer;

    /**
     * Constructor.
     *
     * @param platformContext          the platform context
     * @param addressBook              the address book
     * @param selfId                   the id of this node
     * @param fallenBehindManager      tracks if we are behind or not
     * @param peerAgnosticSyncChecks   peer agnostic checks which are performed to determine whether this node should
     *                                 sync or not
     * @param executor                 the executor to use for parallel read/write operations
     * @param shadowgraph              the shadow graph to sync
     * @param generationsSupplier      a supplier for the current graph generation
     * @param latestEventTipsetTracker the latest event tipset tracker
     * @param gossipEventConsumer      a consumer for gossip events
     */
    public TurboSyncProtocol(
            @NonNull final PlatformContext platformContext,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final PeerAgnosticSyncChecks peerAgnosticSyncChecks,
            @NonNull final ParallelExecutor executor,
            @NonNull final ShadowGraph shadowgraph,
            @NonNull final Supplier<GraphGenerations> generationsSupplier,
            @NonNull final LatestEventTipsetTracker latestEventTipsetTracker,
            @NonNull final InterruptableConsumer<GossipEvent> gossipEventConsumer) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.selfId = Objects.requireNonNull(selfId);
        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.peerAgnosticSyncChecks = Objects.requireNonNull(peerAgnosticSyncChecks);
        this.executor = Objects.requireNonNull(executor);
        this.shadowgraph = Objects.requireNonNull(shadowgraph);
        this.generationsSupplier = Objects.requireNonNull(generationsSupplier);
        this.latestEventTipsetTracker = Objects.requireNonNull(latestEventTipsetTracker);
        this.gossipEventConsumer = Objects.requireNonNull(gossipEventConsumer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldInitiate() {
        return shouldSync();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldAccept() {
        return shouldSync();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptOnSimultaneousInitiate() {
        return shouldSync();
    }

    private boolean shouldSync() {
        return !fallenBehindManager.hasFallenBehind() && peerAgnosticSyncChecks.shouldSync();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void runProtocol(@NonNull final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {

        try {
            new TurboSyncRunner(
                            platformContext,
                            addressBook,
                            selfId,
                            connection.getOtherId(),
                            fallenBehindManager,
                            peerAgnosticSyncChecks,
                            connection,
                            executor,
                            shadowgraph,
                            generationsSupplier,
                            latestEventTipsetTracker,
                            gossipEventConsumer)
                    .run();
        } catch (final ParallelExecutionException e) {
            if (Utilities.isRootCauseSuppliedType(e, IOException.class)) {
                throw new IOException(e);
            }

            throw new NetworkProtocolException(e);
        }
    }
}
