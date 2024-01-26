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

package com.swirlds.platform.gossip;

import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import com.swirlds.platform.gossip.sync.SingleNodeSyncGossip;
import com.swirlds.platform.gossip.sync.SyncGossip;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.PlatformStatusManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Builds the gossip engine, depending on which flavor is requested in the configuration.
 */
public final class GossipFactory {

    private static final Logger logger = LogManager.getLogger(GossipFactory.class);

    private GossipFactory() {}

    /**
     * Builds the gossip engine, depending on which flavor is requested in the configuration.
     *
     * @param platformContext               the platform context
     * @param threadManager                 the thread manager
     * @param time                          the wall clock time
     * @param keysAndCerts                  private keys and public certificates
     * @param notificationEngine            used to send notifications to the app
     * @param addressBook                   the current address book
     * @param selfId                        this node's ID
     * @param appVersion                    the version of the app
     * @param epochHash                     the epoch hash of the initial state
     * @param shadowGraph                   contains non-ancient events
     * @param emergencyRecoveryManager      handles emergency recovery
     * @param receivedEventHandler          handles events received from other nodes
     * @param intakeQueueSizeSupplier       a supplier for the size of the event intake queue
     * @param swirldStateManager            manages the mutable state
     * @param latestCompleteState           holds the latest signed state that has enough signatures to be verifiable
     * @param syncMetrics                   metrics for sync
     * @param platformStatusManager         the platform status manager
     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
     * @param intakeEventCounter            keeps track of the number of events in the intake pipeline from each peer
     * @param emergencyStateSupplier        returns the emergency state if available
     * @return the gossip engine
     */
    public static Gossip buildGossip(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final KeysAndCerts keysAndCerts,
            @NonNull final NotificationEngine notificationEngine,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @Nullable final Hash epochHash,
            @NonNull final Shadowgraph shadowGraph,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager,
            @NonNull final Consumer<GossipEvent> receivedEventHandler,
            @NonNull final LongSupplier intakeQueueSizeSupplier,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final SignedStateNexus latestCompleteState,
            @NonNull final SyncMetrics syncMetrics,
            @NonNull final PlatformStatusManager platformStatusManager,
            @NonNull final Consumer<SignedState> loadReconnectState,
            @NonNull final Runnable clearAllPipelinesForReconnect,
            @NonNull final IntakeEventCounter intakeEventCounter,
            @NonNull final Supplier<ReservedSignedState> emergencyStateSupplier) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(time);
        Objects.requireNonNull(keysAndCerts);
        Objects.requireNonNull(notificationEngine);
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(selfId);
        Objects.requireNonNull(appVersion);
        Objects.requireNonNull(shadowGraph);
        Objects.requireNonNull(emergencyRecoveryManager);
        Objects.requireNonNull(receivedEventHandler);
        Objects.requireNonNull(intakeQueueSizeSupplier);
        Objects.requireNonNull(swirldStateManager);
        Objects.requireNonNull(latestCompleteState);
        Objects.requireNonNull(syncMetrics);
        Objects.requireNonNull(platformStatusManager);
        Objects.requireNonNull(loadReconnectState);
        Objects.requireNonNull(clearAllPipelinesForReconnect);
        Objects.requireNonNull(intakeEventCounter);

        if (addressBook.getSize() == 1) {
            logger.info(STARTUP.getMarker(), "Using SingleNodeSyncGossip");
            return new SingleNodeSyncGossip(
                    platformContext,
                    threadManager,
                    time,
                    keysAndCerts,
                    addressBook,
                    selfId,
                    appVersion,
                    intakeQueueSizeSupplier,
                    swirldStateManager,
                    latestCompleteState,
                    platformStatusManager,
                    loadReconnectState,
                    clearAllPipelinesForReconnect);
        } else {
            logger.info(STARTUP.getMarker(), "Using SyncGossip");
            return new SyncGossip(
                    platformContext,
                    threadManager,
                    time,
                    keysAndCerts,
                    notificationEngine,
                    addressBook,
                    selfId,
                    appVersion,
                    epochHash,
                    shadowGraph,
                    emergencyRecoveryManager,
                    receivedEventHandler,
                    intakeQueueSizeSupplier,
                    swirldStateManager,
                    latestCompleteState,
                    syncMetrics,
                    platformStatusManager,
                    loadReconnectState,
                    clearAllPipelinesForReconnect,
                    intakeEventCounter,
                    emergencyStateSupplier);
        }
    }
}
