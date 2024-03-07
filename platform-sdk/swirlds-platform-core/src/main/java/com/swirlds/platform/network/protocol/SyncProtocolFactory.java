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

package com.swirlds.platform.network.protocol;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.gossip.SyncPermitProvider;
import com.swirlds.platform.gossip.shadowgraph.ShadowgraphSynchronizer;
import com.swirlds.platform.gossip.sync.protocol.SyncProtocol;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.system.status.PlatformStatusGetter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Implementation of a factory for sync protocol
 */
public class SyncProtocolFactory implements ProtocolFactory {

    private final PlatformContext platformContext;
    private final ShadowgraphSynchronizer synchronizer;
    private final FallenBehindManager fallenBehindManager;
    private final SyncPermitProvider permitProvider;
    private final BooleanSupplier gossipHalted;
    private final BooleanSupplier intakeIsTooFull;
    private final Duration sleepAfterSync;
    private final SyncMetrics syncMetrics;
    private final PlatformStatusGetter platformStatusGetter;

    /**
     * Constructs a new sync protocol
     *
     * @param platformContext      the platform context
     * @param synchronizer         the shadow graph synchronizer, responsible for actually doing the sync
     * @param fallenBehindManager  manager to determine whether this node has fallen behind
     * @param permitProvider       provides permits to sync
     * @param gossipHalted         returns true if gossip is halted, false otherwise
     * @param intakeIsTooFull      returns true if the intake queue is too full to continue syncing, false otherwise
     * @param sleepAfterSync       the amount of time to sleep after a sync
     * @param syncMetrics          metrics tracking syncing
     * @param platformStatusGetter provides the current platform status
     */
    public SyncProtocolFactory(
            @NonNull final PlatformContext platformContext,
            @NonNull final ShadowgraphSynchronizer synchronizer,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final SyncPermitProvider permitProvider,
            @NonNull final BooleanSupplier gossipHalted,
            @NonNull final BooleanSupplier intakeIsTooFull,
            @NonNull final Duration sleepAfterSync,
            @NonNull final SyncMetrics syncMetrics,
            @NonNull final PlatformStatusGetter platformStatusGetter) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.synchronizer = Objects.requireNonNull(synchronizer);
        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.permitProvider = Objects.requireNonNull(permitProvider);
        this.gossipHalted = Objects.requireNonNull(gossipHalted);
        this.intakeIsTooFull = Objects.requireNonNull(intakeIsTooFull);
        this.sleepAfterSync = Objects.requireNonNull(sleepAfterSync);
        this.syncMetrics = Objects.requireNonNull(syncMetrics);
        this.platformStatusGetter = Objects.requireNonNull(platformStatusGetter);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public SyncProtocol build(@NonNull final NodeId peerId) {
        return new SyncProtocol(
                platformContext,
                Objects.requireNonNull(peerId),
                synchronizer,
                fallenBehindManager,
                permitProvider,
                gossipHalted,
                intakeIsTooFull,
                sleepAfterSync,
                syncMetrics,
                platformStatusGetter);
    }
}
