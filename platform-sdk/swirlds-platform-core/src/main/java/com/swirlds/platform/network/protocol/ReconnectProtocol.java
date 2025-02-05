/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static com.swirlds.platform.consensus.ConsensusConstants.ROUND_UNDEFINED;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.gossip.modular.GossipController;
import com.swirlds.platform.gossip.modular.SyncGossipSharedProtocolState;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.reconnect.*;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateValidator;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.hiero.consensus.gossip.FallenBehindManager;

/**
 * Implementation of a factory for reconnect protocol
 */
public class ReconnectProtocol implements Protocol {

    private final ReconnectThrottle reconnectThrottle;
    private final Supplier<ReservedSignedState> lastCompleteSignedState;
    private final Duration reconnectSocketTimeout;
    private final ReconnectMetrics reconnectMetrics;
    private final ReconnectController reconnectController;
    private final SignedStateValidator validator;
    private final ThreadManager threadManager;
    private final FallenBehindManager fallenBehindManager;

    /**
     * Provides the platform status.
     */
    private final Supplier<PlatformStatus> platformStatusSupplier;

    private final Configuration configuration;

    private final Time time;
    private final PlatformContext platformContext;

    public ReconnectProtocol(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final ReconnectThrottle reconnectThrottle,
            @NonNull final Supplier<ReservedSignedState> lastCompleteSignedState,
            @NonNull final Duration reconnectSocketTimeout,
            @NonNull final ReconnectMetrics reconnectMetrics,
            @NonNull final ReconnectController reconnectController,
            @NonNull final SignedStateValidator validator,
            @NonNull final FallenBehindManager fallenBehindManager,
            final Supplier<PlatformStatus> platformStatusSupplier,
            @NonNull final Configuration configuration) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.threadManager = Objects.requireNonNull(threadManager);
        this.reconnectThrottle = Objects.requireNonNull(reconnectThrottle);
        this.lastCompleteSignedState = Objects.requireNonNull(lastCompleteSignedState);
        this.reconnectSocketTimeout = Objects.requireNonNull(reconnectSocketTimeout);
        this.reconnectMetrics = Objects.requireNonNull(reconnectMetrics);
        this.reconnectController = Objects.requireNonNull(reconnectController);
        this.validator = Objects.requireNonNull(validator);
        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.platformStatusSupplier = Objects.requireNonNull(platformStatusSupplier);
        this.configuration = Objects.requireNonNull(configuration);
        this.time = Objects.requireNonNull(platformContext.getTime());
    }

    /**
     * Utility method for creating ReconnectProtocol from shared state, while staying compatible with pre-refactor code
     * @param platformContext       the platform context
     * @param sharedState           temporary class to share state between various protocols in modularized gossip, to be removed
     * @param threadManager         the thread manager
     * @param latestCompleteState   holds the latest signed state that has enough signatures to be verifiable
     * @param roster                the current roster
     * @param loadReconnectState    a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
     * @param swirldStateManager    manages the mutable state
     * @param selfId                this node's ID
     * @param gossipController      way to pause/resume gossip while reconnect is in progress
     * @return constructed ReconnectProtocol
     */
    public static ReconnectProtocol create(
            @NonNull final PlatformContext platformContext,
            @NonNull final SyncGossipSharedProtocolState sharedState,
            @NonNull final ThreadManager threadManager,
            @NonNull final Supplier<ReservedSignedState> latestCompleteState,
            @NonNull final Roster roster,
            @NonNull final Consumer<SignedState> loadReconnectState,
            @NonNull final Runnable clearAllPipelinesForReconnect,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final NodeId selfId,
            @NonNull final GossipController gossipController) {

        final ReconnectConfig reconnectConfig =
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class);

        var reconnectThrottle = new ReconnectThrottle(reconnectConfig, platformContext.getTime());

        var reconnectMetrics = new ReconnectMetrics(platformContext.getMetrics(), roster);

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);

        final LongSupplier getRoundSupplier = () -> {
            try (final ReservedSignedState reservedState = latestCompleteState.get()) {
                if (reservedState == null || reservedState.isNull()) {
                    return ROUND_UNDEFINED;
                }

                return reservedState.get().getRound();
            }
        };

        var reconnectHelper = new ReconnectHelper(
                gossipController::pause,
                clearAllPipelinesForReconnect::run,
                swirldStateManager::getConsensusState,
                getRoundSupplier,
                new ReconnectLearnerThrottle(platformContext.getTime(), selfId, reconnectConfig),
                state -> {
                    loadReconnectState.accept(state);
                    sharedState
                            .syncManager()
                            .resetFallenBehind(); // this is almost direct communication to SyncProtocol
                },
                new ReconnectLearnerFactory(
                        platformContext, threadManager, roster, reconnectConfig.asyncStreamTimeout(), reconnectMetrics),
                stateConfig);
        var reconnectController =
                new ReconnectController(reconnectConfig, threadManager, reconnectHelper, gossipController::resume);

        sharedState.fallenBehindCallback().set(reconnectController::start);

        return new ReconnectProtocol(
                platformContext,
                threadManager,
                reconnectThrottle,
                latestCompleteState,
                reconnectConfig.asyncStreamTimeout(),
                reconnectMetrics,
                reconnectController,
                new DefaultSignedStateValidator(platformContext),
                sharedState.syncManager(),
                sharedState.currentPlatformStatus()::get,
                platformContext.getConfiguration());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ReconnectPeerProtocol createPeerInstance(@NonNull final NodeId peerId) {
        return new ReconnectPeerProtocol(
                platformContext,
                threadManager,
                Objects.requireNonNull(peerId),
                reconnectThrottle,
                lastCompleteSignedState,
                reconnectSocketTimeout,
                reconnectMetrics,
                reconnectController,
                validator,
                fallenBehindManager,
                platformStatusSupplier,
                configuration,
                time);
    }
}
