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

package com.swirlds.platform;

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.base.ArgumentUtils;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.Startable;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.connectivity.ConnectionServer;
import com.swirlds.platform.network.connectivity.InboundConnectionHandler;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticConnectionManagers;
import com.swirlds.platform.state.signed.LoadableFromSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Gossips with other nodes to exchange events.
 */
abstract class GossipNetwork implements Startable, LoadableFromSignedState {

    protected final PlatformContext platformContext;
    protected final ThreadManager threadManager;
    protected final Crypto crypto;
    protected final Settings settings;
    protected final AddressBook initialAddressBook;
    protected final NodeId selfId;
    protected final SoftwareVersion appVersion;
    protected final NetworkTopology topology;
    protected final ConnectionTracker connectionTracker;

    /**
     * Start the gossip network.
     */
    public GossipNetwork(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Crypto crypto,
            @NonNull final Settings settings,
            @NonNull final AddressBook initialAddressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final NetworkTopology topology,
            @NonNull final ConnectionTracker connectionTracker) {

        this.platformContext = throwArgNull(platformContext, "platformContext");
        this.crypto = throwArgNull(crypto, "crypto");
        this.settings = throwArgNull(settings, "settings");
        this.initialAddressBook = throwArgNull(initialAddressBook, "initialAddressBook");
        this.selfId = throwArgNull(selfId, "selfId");
        this.appVersion = throwArgNull(appVersion, "appVersion");
        this.threadManager = ArgumentUtils.throwArgNull(threadManager, "threadManager");
        this.topology = ArgumentUtils.throwArgNull(topology, "topology");
        this.connectionTracker = ArgumentUtils.throwArgNull(connectionTracker, "connectionTracker");
    }

    /**
     * Construct and start all networking components that are common to both types of networks, sync and chatter. This
     * is everything related to creating and managing connections to neighbors.Only the connection managers are returned
     * since this should be the only point of entry for other components.
     *
     * @return an instance that maintains connection managers for all connections to neighbors
     */
    public StaticConnectionManagers startCommonNetwork() {
        final SocketFactory socketFactory = PlatformConstructor.socketFactory(
                crypto.getKeysAndCerts(), platformContext.getConfiguration().getConfigData(CryptoConfig.class));
        // create an instance that can create new outbound connections
        final OutboundConnectionCreator connectionCreator = new OutboundConnectionCreator(
                selfId,
                StaticSettingsProvider.getSingleton(),
                connectionTracker,
                socketFactory,
                initialAddressBook,
                !settings.getChatter().isChatterUsed(),
                appVersion);
        final StaticConnectionManagers connectionManagers = new StaticConnectionManagers(topology, connectionCreator);
        final InboundConnectionHandler inboundConnectionHandler = new InboundConnectionHandler(
                connectionTracker,
                selfId,
                initialAddressBook,
                connectionManagers::newConnection,
                StaticSettingsProvider.getSingleton(),
                !settings.getChatter().isChatterUsed(),
                appVersion);
        // allow other members to create connections to me
        final Address address = initialAddressBook.getAddress(selfId.getId());
        final ConnectionServer connectionServer = new ConnectionServer(
                threadManager,
                address.getListenAddressIpv4(),
                address.getListenPortIpv4(),
                socketFactory,
                inboundConnectionHandler::handle);
        new StoppableThreadConfiguration<>(threadManager)
                .setPriority(settings.getThreadPrioritySync())
                .setNodeId(selfId.getId())
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("connectionServer")
                .setWork(connectionServer)
                .build()
                .start();
        return connectionManagers;
    }

    /**
     * Permanently stop the gossip system.
     */
    public abstract void halt();

    /**
     * Stop the gossip system, but allow it to be restarted.
     */
    public abstract void stopGossip();

    /**
     * Start the gossip system after it has been stopped.
     */
    public abstract void startGossip();

    /**
     * Get things that need to be cleared when the platform is stopped for a reconnect.
     * @return a list of all the objects that need to be cleared when the platform is stopped for a reconnect
     */
    public abstract @NonNull List<Pair<Clearable, String>> getClearables();
}
