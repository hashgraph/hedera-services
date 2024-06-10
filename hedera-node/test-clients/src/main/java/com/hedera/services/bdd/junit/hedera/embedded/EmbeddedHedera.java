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

package com.hedera.services.bdd.junit.hedera.embedded;

import static com.hedera.hapi.util.HapiUtils.parseAccount;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader.loadConfigFile;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.Hedera;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.fixtures.state.FakeServiceMigrator;
import com.hedera.node.app.fixtures.state.FakeServicesRegistry;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.base.time.Time;
import com.swirlds.common.concurrent.ExecutorFactory;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultPlatformMetrics;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.metrics.platform.PlatformMetricsFactoryImpl;
import com.swirlds.common.notification.Listener;
import com.swirlds.common.notification.Notification;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.NotificationResult;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.config.TransactionConfig;
import com.swirlds.platform.listeners.PlatformStatusChangeListener;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.state.spi.info.SelfNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * An embedded Hedera node that can be used in tests.
 */
public class EmbeddedHedera {
    private static final int MAX_PLATFORM_TXN_SIZE = 1024 * 6;
    private static final int MAX_QUERY_RESPONSE_SIZE = 1024 * 1024 * 2;

    private final Hedera hedera;
    private final NodeId defaultNodeId;
    private final AddressBook addressBook;
    private final ToyPlatform platform = new ToyPlatform();
    private final PlatformState platformState = new PlatformState();
    private final Map<AccountID, NodeId> nodeIds;
    private final Map<NodeId, com.hedera.hapi.node.base.AccountID> accountIds;
    private final FakeHederaState state = new FakeHederaState();
    private final ThreadLocal<NodeId> submittingNodeId = new ThreadLocal<>();

    @Nullable
    private HederaSoftwareVersion version;

    public EmbeddedHedera(@NonNull final Path bookPath) {
        requireNonNull(bookPath);
        addressBook = loadAddressBook(bookPath);
        defaultNodeId = addressBook.getNodeId(0);
        nodeIds = stream(spliteratorUnknownSize(addressBook.iterator(), 0), false)
                .collect(toMap(EmbeddedHedera::accountIdOf, Address::getNodeId));
        accountIds = stream(spliteratorUnknownSize(addressBook.iterator(), 0), false)
                .collect(toMap(Address::getNodeId, address -> parseAccount(address.getMemo())));
        final var servicesRegistry = new FakeServicesRegistry();
        final var servicesMigrator = new FakeServiceMigrator(servicesRegistry);
        hedera = new Hedera(
                ConstructableRegistry.getInstance(), servicesRegistry, servicesMigrator, (ignore, nodeVersion) -> {
                    this.version = nodeVersion;
                    return new ChameleonSelfNodeInfo(nodeVersion);
                });
    }

    /**
     * Starts the embedded Hedera node.
     */
    public void start() {
        platform.start();
        hedera.onStateInitialized(state, platform, platformState, GENESIS, null);
        hedera.init(platform, defaultNodeId);
        requireNonNull(platform.notificationEngine.statusChangeListener)
                .notify(new PlatformStatusChangeNotification(ACTIVE));
    }

    /**
     * Submits a transaction to the network.
     *
     * @param transaction the transaction to submit
     * @param nodeAccountId the account ID of the node to submit the transaction to
     * @return the response to the transaction
     */
    public TransactionResponse submit(@NonNull final Transaction transaction, @NonNull final AccountID nodeAccountId) {
        requireNonNull(transaction);
        requireNonNull(nodeAccountId);
        setSubmittingNodeId(nodeAccountId);
        final var responseBuffer = BufferedData.allocate(MAX_PLATFORM_TXN_SIZE);
        hedera.ingestWorkflow().submitTransaction(Bytes.wrap(transaction.toByteArray()), responseBuffer);
        submittingNodeId.remove();
        return parseTransactionResponse(responseBuffer);
    }

    /**
     * Sends a query to the network.
     *
     * @param query the query to send
     * @param nodeAccountId the account ID of the node to send the query to
     * @return the response to the query
     */
    public Response send(@NonNull final Query query, @NonNull final AccountID nodeAccountId) {
        requireNonNull(query);
        requireNonNull(nodeAccountId);
        setSubmittingNodeId(nodeAccountId);
        final var responseBuffer = BufferedData.allocate(MAX_QUERY_RESPONSE_SIZE);
        hedera.queryWorkflow().handleQuery(Bytes.wrap(query.toByteArray()), responseBuffer);
        submittingNodeId.remove();
        return parseQueryResponse(responseBuffer);
    }

    private void setSubmittingNodeId(@NonNull final AccountID nodeAccountId) {
        final var nodeId = requireNonNull(nodeIds.get(nodeAccountId));
        submittingNodeId.set(nodeId);
    }

    private @NonNull NodeId effectiveNodeId() {
        return Optional.ofNullable(submittingNodeId.get()).orElse(defaultNodeId);
    }

    private class ToyPlatform implements Platform {
        private static final int MIN_CAPACITY = 5_000;
        private static final long NANOS_BETWEEN_CONS_EVENTS = 1_000;
        private static final Duration ROUND_DURATION = Duration.ofMillis(1);

        private final AtomicLong roundNo = new AtomicLong(1);
        private final AtomicLong consensusOrder = new AtomicLong(1);
        private final PlatformContext platformContext = new ToyPlatformContext();
        private final ExecutorService executor = newSingleThreadExecutor();
        private final AtomicBoolean timeToStop = new AtomicBoolean(false);
        private final ToyNotificationEngine notificationEngine = new ToyNotificationEngine();
        private final BlockingQueue<ToyEvent> queue = new ArrayBlockingQueue<>(MIN_CAPACITY);

        @Override
        public void start() {
            executor.execute(this::handleTransactions);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                timeToStop.set(true);
                executor.shutdown();
            }));
        }

        @Override
        public boolean createTransaction(@NonNull byte[] transaction) {
            return queue.add(new ToyEvent(effectiveNodeId(), Instant.now(), new SwirldTransaction(transaction)));
        }

        @NonNull
        @Override
        public Signature sign(@NonNull byte[] data) {
            throw new AssertionError("Not implemented");
        }

        @NonNull
        @Override
        public AddressBook getAddressBook() {
            return addressBook;
        }

        @NonNull
        @Override
        public NotificationEngine getNotificationEngine() {
            return notificationEngine;
        }

        @NonNull
        @Override
        public PlatformContext getContext() {
            return platformContext;
        }

        @NonNull
        @Override
        public NodeId getSelfId() {
            throw new UnsupportedOperationException("Not used by Hedera");
        }

        @NonNull
        @Override
        public <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState(@NonNull String reason) {
            throw new UnsupportedOperationException("Not used by Hedera");
        }

        private void handleTransactions() {
            Instant then = Instant.now();
            final List<ToyEvent> events = new ArrayList<>();
            while (!timeToStop.get()) {
                final var now = Instant.now();
                try {
                    if (Duration.between(then, now).compareTo(ROUND_DURATION) >= 0) {
                        final var firstConsTime = then;
                        final var consensusEvents = IntStream.range(0, events.size())
                                .<ConsensusEvent>mapToObj(i -> new ToyConsensusEvent(
                                        events.get(i),
                                        consensusOrder.getAndIncrement(),
                                        firstConsTime.plusNanos(i * NANOS_BETWEEN_CONS_EVENTS)))
                                .toList();
                        final var round = new ToyRound(roundNo.getAndIncrement(), now, consensusEvents);
                        then = Instant.now();
                        hedera.handleWorkflow().handleRound(state, platformState, round);
                        events.clear();
                    }
                    final var event = queue.take();
                    hedera.onPreHandle(event, state);
                    events.add(event);
                } catch (final InterruptedException e) {
                    // Thread interrupted because of shutdown.
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private class ToyPlatformContext implements PlatformContext {
        private final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(MetricsConfig.class)
                .withConfigDataType(TransactionConfig.class)
                .build();

        private final Metrics metrics;

        public ToyPlatformContext() {
            final var metricsConfig = configuration.getConfigData(MetricsConfig.class);
            this.metrics = new DefaultPlatformMetrics(
                    defaultNodeId,
                    new MetricKeyRegistry(),
                    Executors.newSingleThreadScheduledExecutor(),
                    new PlatformMetricsFactoryImpl(metricsConfig),
                    metricsConfig);
        }

        @NonNull
        @Override
        public Configuration getConfiguration() {
            return ConfigurationBuilder.create()
                    .withConfigDataType(TransactionConfig.class)
                    .build();
        }

        @NonNull
        @Override
        public Cryptography getCryptography() {
            return CryptographyHolder.get();
        }

        @NonNull
        @Override
        public Metrics getMetrics() {
            return metrics;
        }

        @NonNull
        @Override
        public Time getTime() {
            throw new UnsupportedOperationException("Not used by Hedera");
        }

        @NonNull
        @Override
        public FileSystemManager getFileSystemManager() {
            throw new UnsupportedOperationException("Not used by Hedera");
        }

        @NonNull
        @Override
        public ExecutorFactory getExecutorFactory() {
            throw new UnsupportedOperationException("Not used by Hedera");
        }

        @NonNull
        @Override
        public RecycleBin getRecycleBin() {
            throw new UnsupportedOperationException("Not used by Hedera");
        }
    }

    private class ChameleonSelfNodeInfo implements SelfNodeInfo {
        private final HederaSoftwareVersion version;

        public ChameleonSelfNodeInfo(@NonNull final HederaSoftwareVersion version) {
            this.version = requireNonNull(version);
        }

        @NonNull
        @Override
        public SemanticVersion hapiVersion() {
            return version.getHapiVersion();
        }

        @NonNull
        @Override
        public SemanticVersion appVersion() {
            return version.getServicesVersion();
        }

        @Override
        public long nodeId() {
            return effectiveNodeId().id();
        }

        @Override
        public com.hedera.hapi.node.base.AccountID accountId() {
            return accountIds.get(effectiveNodeId());
        }

        @Override
        public String memo() {
            return addressBook.getAddress(effectiveNodeId()).getMemo();
        }

        @Override
        public String externalHostName() {
            return addressBook.getAddress(effectiveNodeId()).getHostnameExternal();
        }

        @Override
        public int externalPort() {
            return addressBook.getAddress(effectiveNodeId()).getPortExternal();
        }

        @Override
        public String hexEncodedPublicKey() {
            return hex(requireNonNull(addressBook.getAddress(effectiveNodeId()).getSigPublicKey())
                    .getEncoded());
        }

        @Override
        public long stake() {
            return addressBook.getAddress(effectiveNodeId()).getWeight();
        }
    }

    private class ToyNotificationEngine implements NotificationEngine {
        @Nullable
        private PlatformStatusChangeListener statusChangeListener;

        @Override
        public void initialize() {
            throw new UnsupportedOperationException("Not used by Hedera");
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException("Not used by Hedera");
        }

        @Override
        public <L extends Listener<N>, N extends Notification> Future<NotificationResult<N>> dispatch(
                Class<L> listenerClass,
                N notification,
                StandardFuture.CompletionCallback<NotificationResult<N>> notificationsCompletedCallback) {
            throw new UnsupportedOperationException("Not used by Hedera");
        }

        @Override
        public <L extends Listener<?>> boolean register(Class<L> listenerClass, L callback) {
            if (listenerClass == PlatformStatusChangeListener.class) {
                this.statusChangeListener = (PlatformStatusChangeListener) callback;
            }
            return false;
        }

        @Override
        public <L extends Listener<?>> boolean unregister(Class<L> listenerClass, L callback) {
            throw new UnsupportedOperationException("Not used by Hedera");
        }

        @Override
        public void unregisterAll() {
            throw new UnsupportedOperationException("Not used by Hedera");
        }
    }

    private class ToyEvent implements Event {
        private final NodeId creatorId;
        private final Instant timeCreated;
        protected final SwirldTransaction transaction;

        public ToyEvent(
                @NonNull final NodeId creatorId,
                @NonNull final Instant timeCreated,
                @NonNull final SwirldTransaction transaction) {
            this.creatorId = creatorId;
            this.timeCreated = timeCreated;
            this.transaction = transaction;
        }

        @Override
        public Iterator<com.swirlds.platform.system.transaction.Transaction> transactionIterator() {
            return Collections.singleton((com.swirlds.platform.system.transaction.Transaction) transaction)
                    .iterator();
        }

        @Override
        public Instant getTimeCreated() {
            return timeCreated;
        }

        @NonNull
        @Override
        public NodeId getCreatorId() {
            return creatorId;
        }

        @Nullable
        @Override
        public SoftwareVersion getSoftwareVersion() {
            return requireNonNull(version);
        }
    }

    private class ToyConsensusEvent extends ToyEvent implements ConsensusEvent {
        private final long consensusOrder;
        private final Instant consensusTimestamp;

        public ToyConsensusEvent(
                @NonNull final ToyEvent event, final long consensusOrder, @NonNull final Instant consensusTimestamp) {
            super(event.creatorId, event.timeCreated, event.transaction);
            this.consensusOrder = consensusOrder;
            this.consensusTimestamp = requireNonNull(consensusTimestamp);
        }

        @Override
        public Iterator<ConsensusTransaction> consensusTransactionIterator() {
            return Collections.singleton((ConsensusTransaction) transaction).iterator();
        }

        @Override
        public long getConsensusOrder() {
            return consensusOrder;
        }

        @Override
        public Instant getConsensusTimestamp() {
            return consensusTimestamp;
        }
    }

    private class ToyRound implements Round {
        private final long roundNum;
        private final Instant now;
        private final List<ConsensusEvent> consensusEvents;

        public ToyRound(
                final long roundNum, @NonNull final Instant now, @NonNull final List<ConsensusEvent> consensusEvents) {
            this.roundNum = roundNum;
            this.now = requireNonNull(now);
            this.consensusEvents = requireNonNull(consensusEvents);
        }

        @NonNull
        @Override
        public Iterator<ConsensusEvent> iterator() {
            return consensusEvents.iterator();
        }

        @Override
        public long getRoundNum() {
            return roundNum;
        }

        @Override
        public boolean isEmpty() {
            return consensusEvents.isEmpty();
        }

        @Override
        public int getEventCount() {
            return consensusEvents.size();
        }

        @NonNull
        @Override
        public AddressBook getConsensusRoster() {
            return addressBook;
        }

        @NonNull
        @Override
        public Instant getConsensusTimestamp() {
            return now;
        }
    }

    private static AddressBook loadAddressBook(@NonNull final Path path) {
        requireNonNull(path);
        final var configFile = loadConfigFile(path.toAbsolutePath());
        final var randomAddressBook = RandomAddressBookBuilder.create(new Random())
                .withSize(1)
                .withRealKeysEnabled(true)
                .build();
        final var sigCert = requireNonNull(randomAddressBook.iterator().next().getSigCert());
        final var addressBook = configFile.getAddressBook();
        return new AddressBook(stream(spliteratorUnknownSize(addressBook.iterator(), 0), false)
                .map(address -> address.copySetSigCert(sigCert))
                .toList());
    }

    private static TransactionResponse parseTransactionResponse(@NonNull final BufferedData responseBuffer) {
        responseBuffer.flip();
        try {
            return TransactionResponse.parseFrom(responseBuffer.asInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Response parseQueryResponse(@NonNull final BufferedData responseBuffer) {
        final byte[] bytes = new byte[Math.toIntExact(responseBuffer.position())];
        responseBuffer.resetPosition();
        responseBuffer.readBytes(bytes);
        try {
            return Response.parseFrom(responseBuffer.asInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static AccountID accountIdOf(@NonNull final Address address) {
        return fromPbj(parseAccount(address.getMemo()));
    }
}
