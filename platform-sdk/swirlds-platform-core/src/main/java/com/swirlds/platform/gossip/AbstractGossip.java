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

import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.base.state.Startable;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.config.BasicConfig;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.gossip.sync.SyncManagerImpl;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.SocketConfig;
import com.swirlds.platform.network.connectivity.ConnectionServer;
import com.swirlds.platform.network.connectivity.InboundConnectionHandler;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.connectivity.TcpFactory;
import com.swirlds.platform.network.connectivity.TlsFactory;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticConnectionManagers;
import com.swirlds.platform.network.topology.StaticTopology;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectLearnerFactory;
import com.swirlds.platform.reconnect.ReconnectLearnerThrottle;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.PlatformConstructionException;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Boilerplate code for gossip.
 */
public abstract class AbstractGossip implements ConnectionTracker, Gossip {
    private LifecyclePhase lifecyclePhase = LifecyclePhase.NOT_STARTED;

    protected final PlatformContext platformContext;
    protected final AddressBook addressBook;
    protected final NodeId selfId;
    protected final NetworkTopology topology;
    protected final NetworkMetrics networkMetrics;
    protected final ReconnectHelper reconnectHelper;
    protected final StaticConnectionManagers connectionManagers;
    protected final FallenBehindManagerImpl fallenBehindManager;
    protected final SyncManagerImpl syncManager;
    protected final ReconnectThrottle reconnectThrottle;
    protected final ReconnectMetrics reconnectMetrics;

    /**
     * Enables submitting platform status actions
     */
    protected final StatusActionSubmitter statusActionSubmitter;

    protected final List<Startable> thingsToStart = new ArrayList<>();

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
     * @param intakeQueueSizeSupplier       a supplier for the size of the event intake queue
     * @param swirldStateManager            manages the mutable state
     * @param latestCompleteState           holds the latest signed state that has enough signatures to be verifiable
     * @param statusActionSubmitter         enables submitting platform status actions
     * @param loadReconnectState            a method that should be called when a state from reconnect is obtained
     * @param clearAllPipelinesForReconnect this method should be called to clear all pipelines prior to a reconnect
     */
    protected AbstractGossip(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final KeysAndCerts keysAndCerts,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final LongSupplier intakeQueueSizeSupplier,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final SignedStateNexus latestCompleteState,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final Consumer<SignedState> loadReconnectState,
            @NonNull final Runnable clearAllPipelinesForReconnect) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.selfId = Objects.requireNonNull(selfId);
        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);
        Objects.requireNonNull(time);

        final ThreadConfig threadConfig = platformContext.getConfiguration().getConfigData(ThreadConfig.class);

        final BasicConfig basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);
        final CryptoConfig cryptoConfig = platformContext.getConfiguration().getConfigData(CryptoConfig.class);
        final SocketConfig socketConfig = platformContext.getConfiguration().getConfigData(SocketConfig.class);

        topology = new StaticTopology(
                addressBook, selfId, basicConfig.numConnections(), unidirectionalConnectionsEnabled());

        final SocketFactory socketFactory = socketFactory(keysAndCerts, cryptoConfig, socketConfig);
        // create an instance that can create new outbound connections
        final OutboundConnectionCreator connectionCreator = new OutboundConnectionCreator(
                platformContext, selfId, this, socketFactory, addressBook, shouldDoVersionCheck(), appVersion);
        connectionManagers = new StaticConnectionManagers(topology, connectionCreator);
        final InboundConnectionHandler inboundConnectionHandler = new InboundConnectionHandler(
                platformContext,
                this,
                selfId,
                addressBook,
                connectionManagers::newConnection,
                shouldDoVersionCheck(),
                appVersion,
                time);
        // allow other members to create connections to me
        final Address address = addressBook.getAddress(selfId);
        final ConnectionServer connectionServer = new ConnectionServer(
                threadManager,
                address.getListenAddressIpv4(),
                address.getListenPort(),
                socketFactory,
                inboundConnectionHandler::handle);
        thingsToStart.add(new StoppableThreadConfiguration<>(threadManager)
                .setPriority(threadConfig.threadPrioritySync())
                .setNodeId(selfId)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("connectionServer")
                .setWork(connectionServer)
                .build());

        fallenBehindManager = buildFallenBehindManager();

        syncManager = new SyncManagerImpl(
                platformContext,
                intakeQueueSizeSupplier,
                fallenBehindManager,
                platformContext.getConfiguration().getConfigData(EventConfig.class));

        final ReconnectConfig reconnectConfig =
                platformContext.getConfiguration().getConfigData(ReconnectConfig.class);
        reconnectThrottle = new ReconnectThrottle(reconnectConfig, time);

        networkMetrics = new NetworkMetrics(platformContext.getMetrics(), selfId, addressBook);
        platformContext.getMetrics().addUpdater(networkMetrics::update);

        reconnectMetrics = new ReconnectMetrics(platformContext.getMetrics(), addressBook);

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        reconnectHelper = new ReconnectHelper(
                this::pause,
                clearAllPipelinesForReconnect::run,
                swirldStateManager::getConsensusState,
                latestCompleteState::getRound,
                new ReconnectLearnerThrottle(time, selfId, reconnectConfig),
                loadReconnectState,
                new ReconnectLearnerFactory(
                        platformContext,
                        threadManager,
                        addressBook,
                        reconnectConfig.asyncStreamTimeout(),
                        reconnectMetrics),
                stateConfig);
    }

    private static SocketFactory socketFactory(
            @NonNull final KeysAndCerts keysAndCerts,
            @NonNull final CryptoConfig cryptoConfig,
            @NonNull final SocketConfig socketConfig) {
        Objects.requireNonNull(keysAndCerts);
        Objects.requireNonNull(cryptoConfig);
        Objects.requireNonNull(socketConfig);

        if (!socketConfig.useTLS()) {
            return new TcpFactory(socketConfig);
        }
        try {
            return new TlsFactory(keysAndCerts, socketConfig, cryptoConfig);
        } catch (final NoSuchAlgorithmException
                | UnrecoverableKeyException
                | KeyStoreException
                | KeyManagementException
                | CertificateException
                | IOException e) {
            throw new PlatformConstructionException("A problem occurred while creating the SocketFactory", e);
        }
    }

    /**
     * Build the fallen behind manager.
     */
    @NonNull
    protected abstract FallenBehindManagerImpl buildFallenBehindManager();

    /**
     * If true, use unidirectional connections between nodes.
     */
    protected abstract boolean unidirectionalConnectionsEnabled();

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public LifecyclePhase getLifecyclePhase() {
        return lifecyclePhase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfNotInPhase(LifecyclePhase.NOT_STARTED);
        lifecyclePhase = LifecyclePhase.STARTED;
        thingsToStart.forEach(Startable::start);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        lifecyclePhase = LifecyclePhase.STOPPED;
        syncManager.haltRequestedObserver("stopping gossip");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetFallenBehind() {
        syncManager.resetFallenBehind();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasFallenBehind() {
        return syncManager.hasFallenBehind();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newConnectionOpened(@NonNull final Connection sc) {
        Objects.requireNonNull(sc);
        networkMetrics.connectionEstablished(sc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connectionClosed(final boolean outbound, @NonNull final Connection conn) {
        Objects.requireNonNull(conn);
        networkMetrics.recordDisconnect(conn);
    }

    /**
     * Should the network layer do a version check prior to initiating a connection?
     *
     * @return true if a version check should be done
     */
    protected abstract boolean shouldDoVersionCheck();
}
