/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.sync;

import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.base.time.Time;
import com.swirlds.base.utility.Pair;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.LoggingClearables;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.CriticalQuorumImpl;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.AbstractGossip;
import com.swirlds.platform.gossip.FallenBehindManagerImpl;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Sync gossip using the protocol negotiator.
 */
public class SingleNodeSyncGossip extends AbstractGossip {

    private static final Logger logger = LogManager.getLogger(SingleNodeSyncGossip.class);

    /**
     * Holds a list of objects that need to be cleared when {@link #clear()} is called on this object.
     */
    private final Clearable clearAllInternalPipelines;

    /**
     * Builds the gossip engine, depending on which flavor is requested in the configuration.
     *
     * @param platformContext               the platform context
     * @param threadManager                 the thread manager
     * @param time                          the time object used to get the current time
     * @param crypto                        can be used to sign things
     * @param addressBook                   the current address book
     * @param selfId                        this node's ID
     * @param appVersion                    the version of the app
     * @param shadowGraph                   contains non-ancient events
     * @param intakeQueue                   the event intake queue
     * @param swirldStateManager            manages the mutable state
     * @param stateManagementComponent      manages the lifecycle of the state
     * @param eventObserverDispatcher       the object used to wire event intake
     * @param syncMetrics                   metrics for sync
     * @param statusActionSubmitter         enables submitting platform status actions
     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
     */
    public SingleNodeSyncGossip(
            @NonNull PlatformContext platformContext,
            @NonNull ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull Crypto crypto,
            @NonNull AddressBook addressBook,
            @NonNull NodeId selfId,
            @NonNull SoftwareVersion appVersion,
            @NonNull final ShadowGraph shadowGraph,
            @NonNull final QueueThread<GossipEvent> intakeQueue,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final EventObserverDispatcher eventObserverDispatcher,
            @NonNull final SyncMetrics syncMetrics,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final Consumer<SignedState> loadReconnectState,
            @NonNull final Runnable clearAllPipelinesForReconnect) {

        super(
                platformContext,
                threadManager,
                time,
                crypto,
                addressBook,
                selfId,
                appVersion,
                intakeQueue,
                swirldStateManager,
                stateManagementComponent,
                syncMetrics,
                eventObserverDispatcher,
                statusActionSubmitter,
                loadReconnectState,
                clearAllPipelinesForReconnect);

        clearAllInternalPipelines = new LoggingClearables(
                RECONNECT.getMarker(),
                List.of(Pair.of(intakeQueue, "intakeQueue"), Pair.of(shadowGraph, "shadowGraph")));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean unidirectionalConnectionsEnabled() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected CriticalQuorum buildCriticalQuorum() {
        return new CriticalQuorumImpl(platformContext.getMetrics(), selfId, addressBook);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected FallenBehindManagerImpl buildFallenBehindManager() {
        return new FallenBehindManagerImpl(
                addressBook,
                selfId,
                topology.getConnectionGraph(),
                statusActionSubmitter,
                // Fallen behind callback is intentional no-op, is impossible to fall behind
                () -> {},
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadFromSignedState(@NonNull final SignedState signedState) {
        // intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        clearAllInternalPipelines.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean shouldDoVersionCheck() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pause() {
        logger.info(RECONNECT.getMarker(), "pause() requested for SingleNodeSyncGossip, this should not be possible");
    }

    @Override
    public void resume() {
        logger.info(RECONNECT.getMarker(), "resume() requested for SingleNodeSyncGossip, this should not be possible");
    }
}
