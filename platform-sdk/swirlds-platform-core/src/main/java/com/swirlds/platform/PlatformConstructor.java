/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.base.function.CheckedConsumer;
import com.swirlds.common.config.SocketConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.platform.components.common.output.RoundAppliedToStateConsumer;
import com.swirlds.platform.components.transaction.system.ConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.system.PreconsensusSystemTransactionManager;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.metrics.ConsensusHandlingMetrics;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.connectivity.TcpFactory;
import com.swirlds.platform.network.connectivity.TlsFactory;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.SwirldStateManagerImpl;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.PlatformConstructionException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;

/**
 * Used to construct platform components that use DI
 */
public final class PlatformConstructor {

    /**
     * The maximum size of the queue holding signed states ready to be hashed and signed by others.
     */
    private static final int STATE_HASH_QUEUE_MAX = 1;

    /**
     * Private constructor so that this class is never instantiated
     */
    private PlatformConstructor() {}

    /**
     * Create a parallel executor.
     *
     * @param threadManager responsible for managing thread lifecycles
     */
    public static ParallelExecutor parallelExecutor(final ThreadManager threadManager) {
        return new CachedPoolParallelExecutor(threadManager, "node-sync");
    }

    public static SocketFactory socketFactory(
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

    public static PlatformSigner platformSigner(@NonNull final KeysAndCerts keysAndCerts) {
        Objects.requireNonNull(keysAndCerts);
        try {
            return new PlatformSigner(keysAndCerts);
        } catch (final NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException e) {
            throw new PlatformConstructionException(e);
        }
    }

    /**
     * Creates the {@link QueueThread} that stores and handles signed states that need to be hashed and have signatures
     * collected.
     *
     * @param threadManager       responsible for managing thread lifecycles
     * @param selfId              this node's id
     * @param signedStateConsumer consumer of signed states that hashes the state and collects signatures
     * @param metrics             the metrics object
     */
    static QueueThread<ReservedSignedState> stateHashSignQueue(
            @NonNull final ThreadManager threadManager,
            @NonNull final NodeId selfId,
            @NonNull final InterruptableConsumer<ReservedSignedState> signedStateConsumer,
            @NonNull final Metrics metrics) {
        Objects.requireNonNull(threadManager, "threadManager must not be null");
        Objects.requireNonNull(selfId, "selfId must not be null");
        Objects.requireNonNull(signedStateConsumer, "signedStateConsumer must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");

        return new QueueThreadConfiguration<ReservedSignedState>(threadManager)
                .setNodeId(selfId)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("state-hash-sign")
                .setHandler(signedStateConsumer)
                .setCapacity(STATE_HASH_QUEUE_MAX)
                .setMetricsConfiguration(new QueueThreadMetricsConfiguration(metrics).enableBusyTimeMetric())
                .build();
    }

    /**
     * Creates a new instance of {@link SwirldStateManager}.
     *
     * @param platformContext                      the platform context
     * @param addressBook                          the address book
     * @param selfId                               this node's id
     * @param preconsensusSystemTransactionManager the manager which handles system transactions pre-consensus
     * @param consensusSystemTransactionManager    the manager which handles system transactions post-consensus
     * @param statusActionSubmitter                enables submitting platform status actions
     * @param initialState                         the initial state
     * @param softwareVersion                      the software version
     * @return the newly constructed instance of {@link SwirldStateManager}
     */
    static SwirldStateManager swirldStateManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final PreconsensusSystemTransactionManager preconsensusSystemTransactionManager,
            @NonNull final ConsensusSystemTransactionManager consensusSystemTransactionManager,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final BooleanSupplier inFreezeChecker,
            @NonNull final State initialState,
            @NonNull final SoftwareVersion softwareVersion) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(selfId);
        Objects.requireNonNull(preconsensusSystemTransactionManager);
        Objects.requireNonNull(consensusSystemTransactionManager);
        Objects.requireNonNull(statusActionSubmitter);
        Objects.requireNonNull(inFreezeChecker);
        Objects.requireNonNull(initialState);
        Objects.requireNonNull(softwareVersion);

        return new SwirldStateManagerImpl(
                platformContext,
                addressBook,
                selfId,
                preconsensusSystemTransactionManager,
                consensusSystemTransactionManager,
                new SwirldStateMetrics(platformContext.getMetrics()),
                statusActionSubmitter,
                inFreezeChecker,
                initialState,
                softwareVersion);
    }

    /**
     * Constructs a new {@link ConsensusRoundHandler}.
     *
     * @param threadManager               responsible for creating and managing threads
     * @param selfId                      this node's id
     * @param swirldStateManager          the instance of {@link SwirldStateManager}
     * @param consensusHandlingMetrics    the class that records stats relating to {@link SwirldStateManager}
     * @param eventStreamManager          the instance that streams consensus events to disk
     * @param stateHashSignQueue          the queue for signed states that need signatures collected
     * @param waitForEventDurability      a method that blocks until an event becomes durable.
     * @param enterFreezePeriod           a runnable executed when a freeze is entered
     * @param statusActionSubmitter       enables submitting platform status actions
     * @param roundAppliedToStateConsumer the consumer to invoke when a round has just been applied to the state
     * @param softwareVersion             the software version of the application
     * @return the newly constructed instance of {@link ConsensusRoundHandler}
     */
    static ConsensusRoundHandler consensusHandler(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final NodeId selfId,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final ConsensusHandlingMetrics consensusHandlingMetrics,
            @NonNull final EventStreamManager<EventImpl> eventStreamManager,
            @NonNull final BlockingQueue<ReservedSignedState> stateHashSignQueue,
            @NonNull final CheckedConsumer<EventImpl, InterruptedException> waitForEventDurability,
            @NonNull final Runnable enterFreezePeriod,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final RoundAppliedToStateConsumer roundAppliedToStateConsumer,
            @NonNull final SoftwareVersion softwareVersion) {

        return new ConsensusRoundHandler(
                platformContext,
                threadManager,
                selfId,
                swirldStateManager,
                consensusHandlingMetrics,
                eventStreamManager,
                stateHashSignQueue,
                waitForEventDurability,
                enterFreezePeriod,
                statusActionSubmitter,
                roundAppliedToStateConsumer,
                softwareVersion);
    }
}
