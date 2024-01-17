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
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.LatestEventTipsetTracker;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A variation of a sync protocol that continuously performs multiple concurrent syncs.
 */
public class TurboSyncProtocol implements Protocol {

    private final PlatformContext platformContext;
    private final AddressBook addressBook;
    private final NodeId selfId;
    private final NodeId peerId;
    private final FallenBehindManager fallenBehindManager;
    private final BooleanSupplier gossipHalted;
    private final BooleanSupplier intakeIsTooFull;
    private final IntakeEventCounter intakeEventCounter;
    private final ParallelExecutor executor;
    private final ShadowGraph shadowgraph;
    private final Supplier<GraphGenerations> generationsSupplier;
    private final LatestEventTipsetTracker latestEventTipsetTracker;
    private final InterruptableConsumer<GossipEvent> gossipEventConsumer;
    private final SyncMetrics syncMetrics;

    /**
     * Constructor.
     *
     * @param platformContext          the platform context
     * @param addressBook              the address book
     * @param selfId                   the id of this node
     * @param peerId                   the id of the peer we are syncing with
     * @param fallenBehindManager      tracks if we are behind or not
     * @param gossipHalted             returns true if gossip is halted, false otherwise
     * @param intakeIsTooFull          returns true if the intake queue is too full, false otherwise
     * @param intakeEventCounter       the intake event counter, counts how many events from each peer are in the intake
     *                                 pipeline
     * @param executor                 the executor to use for parallel read/write operations
     * @param shadowgraph              the shadow graph to sync
     * @param generationsSupplier      a supplier for the current graph generation
     * @param latestEventTipsetTracker the latest event tipset tracker
     * @param gossipEventConsumer      a consumer for gossip events
     * @param syncMetrics              encapsulates sync metrics
     */
    public TurboSyncProtocol(
            @NonNull final PlatformContext platformContext,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final NodeId peerId,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final BooleanSupplier gossipHalted,
            @NonNull final BooleanSupplier intakeIsTooFull,
            @NonNull final IntakeEventCounter intakeEventCounter,
            @NonNull final ParallelExecutor executor,
            @NonNull final ShadowGraph shadowgraph,
            @NonNull final Supplier<GraphGenerations> generationsSupplier,
            @NonNull final LatestEventTipsetTracker latestEventTipsetTracker,
            @NonNull final InterruptableConsumer<GossipEvent> gossipEventConsumer,
            @NonNull final SyncMetrics syncMetrics) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.selfId = Objects.requireNonNull(selfId);
        this.peerId = Objects.requireNonNull(peerId);
        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.gossipHalted = Objects.requireNonNull(gossipHalted);
        this.intakeIsTooFull = Objects.requireNonNull(intakeIsTooFull);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.executor = Objects.requireNonNull(executor);
        this.shadowgraph = Objects.requireNonNull(shadowgraph);
        this.generationsSupplier = Objects.requireNonNull(generationsSupplier);
        this.latestEventTipsetTracker = Objects.requireNonNull(latestEventTipsetTracker);
        this.gossipEventConsumer = Objects.requireNonNull(gossipEventConsumer);
        this.syncMetrics = Objects.requireNonNull(syncMetrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldInitiate() {
        syncMetrics.opportunityToInitiateSync();
        final boolean shouldSync = shouldSync();

        if (shouldSync) {
            syncMetrics.outgoingSyncRequestSent();
        }

        return shouldSync;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldAccept() {
        syncMetrics.incomingSyncRequestReceived();
        final boolean shouldSync = shouldSync();

        if (shouldSync) {
            syncMetrics.acceptedSyncRequest();
        }

        return shouldSync;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptOnSimultaneousInitiate() {
        return shouldSync();
    }

    /**
     * Should we sync?
     *
     * @return true if we should sync, false otherwise
     */
    private boolean shouldSync() {
        if (gossipHalted.getAsBoolean()) {
            syncMetrics.doNotSyncHalted();
            return false;
        }

        if (intakeIsTooFull.getAsBoolean()) {
            syncMetrics.doNotSyncIntakeQueue();
            return false;
        }

        if (fallenBehindManager.hasFallenBehind()) {
            syncMetrics.doNotSyncFallenBehind();
            return false;
        }

        if (intakeEventCounter.hasUnprocessedEvents(peerId)) {
            syncMetrics.doNotSyncIntakeCounter();
            return false;
        }

        return true;
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
                            peerId,
                            fallenBehindManager,
                            gossipHalted,
                            intakeIsTooFull,
                            intakeEventCounter,
                            connection,
                            executor,
                            shadowgraph,
                            generationsSupplier,
                            latestEventTipsetTracker,
                            gossipEventConsumer,
                            syncMetrics)
                    .run();
        } catch (final ParallelExecutionException e) {
            if (Utilities.isRootCauseSuppliedType(e, IOException.class)) {
                throw new IOException(e);
            }

            throw new NetworkProtocolException(e);
        }
    }
}
