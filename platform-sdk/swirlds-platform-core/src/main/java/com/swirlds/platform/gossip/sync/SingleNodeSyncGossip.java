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

package com.swirlds.platform.gossip.sync;

import static com.swirlds.logging.legacy.LogMarker.RECONNECT;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.AbstractGossip;
import com.swirlds.platform.gossip.FallenBehindManagerImpl;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Sync gossip using the protocol negotiator.
 */
public class SingleNodeSyncGossip extends AbstractGossip {

    private static final Logger logger = LogManager.getLogger(SingleNodeSyncGossip.class);

    /**
     * Builds the gossip engine, depending on which flavor is requested in the configuration.
     *
     * @param platformContext               the platform context
     * @param threadManager                 the thread manager
     * @param time                          the time object used to get the current time
     * @param keysAndCerts                  private keys and public certificates
     * @param addressBook                   the current address book
     * @param selfId                        this node's ID
     * @param appVersion                    the version of the app
     * @param intakeQueue                   the event intake queue
     * @param swirldStateManager            manages the mutable state
     * @param latestCompleteState           holds the latest signed state that has enough signatures to be verifiable
     * @param statusActionSubmitter         enables submitting platform status actions
     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
     */
    public SingleNodeSyncGossip(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final KeysAndCerts keysAndCerts,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final QueueThread<GossipEvent> intakeQueue,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final SignedStateNexus latestCompleteState,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final Consumer<SignedState> loadReconnectState,
            @NonNull final Runnable clearAllPipelinesForReconnect) {

        super(
                platformContext,
                threadManager,
                time,
                keysAndCerts,
                addressBook,
                selfId,
                appVersion,
                intakeQueue,
                swirldStateManager,
                latestCompleteState,
                statusActionSubmitter,
                loadReconnectState,
                clearAllPipelinesForReconnect);
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
