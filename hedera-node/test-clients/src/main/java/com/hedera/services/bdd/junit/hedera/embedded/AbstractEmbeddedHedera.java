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
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.ADDRESS_BOOK;
import static com.swirlds.platform.roster.RosterRetriever.buildRoster;
import static com.swirlds.platform.state.service.PbjConverter.toPbjAddressBook;
import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static com.swirlds.platform.system.status.PlatformStatus.FREEZE_COMPLETE;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.node.app.Hedera;
import com.hedera.node.app.fixtures.state.FakeServiceMigrator;
import com.hedera.node.app.fixtures.state.FakeServicesRegistry;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.info.DiskStartupNetworks;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.AbstractFakePlatform;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssBaseService;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.state.notifications.StateHashedNotification;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.WritableSingletonState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation support for {@link EmbeddedHedera}.
 */
public abstract class AbstractEmbeddedHedera implements EmbeddedHedera {
    private static final Logger log = LogManager.getLogger(AbstractEmbeddedHedera.class);

    private static final int NANOS_IN_A_SECOND = 1_000_000_000;
    private static final SemanticVersion EARLIER_SEMVER =
            SemanticVersion.newBuilder().patch(1).build();
    private static final SemanticVersion LATER_SEMVER =
            SemanticVersion.newBuilder().major(999).build();

    protected static final NodeId MISSING_NODE_ID = NodeId.of(666L);
    protected static final int MAX_PLATFORM_TXN_SIZE = 1024 * 6;
    protected static final int MAX_QUERY_RESPONSE_SIZE = 1024 * 1024 * 2;
    protected static final Hash FAKE_START_OF_STATE_HASH = new Hash(new byte[48]);
    protected static final TransactionResponse OK_RESPONSE = TransactionResponse.getDefaultInstance();
    protected static final PlatformStatusChangeNotification ACTIVE_NOTIFICATION =
            new PlatformStatusChangeNotification(ACTIVE);
    protected static final PlatformStatusChangeNotification FREEZE_COMPLETE_NOTIFICATION =
            new PlatformStatusChangeNotification(FREEZE_COMPLETE);

    private final boolean blockStreamEnabled;

    protected final Map<AccountID, NodeId> nodeIds;
    protected final Map<NodeId, com.hedera.hapi.node.base.AccountID> accountIds;
    protected final FakeState state = new FakeState();
    protected final AccountID defaultNodeAccountId;
    protected final AddressBook addressBook;
    protected final Roster roster;
    protected final NodeId defaultNodeId;
    protected final AtomicInteger nextNano = new AtomicInteger(0);
    protected final Hedera hedera;
    protected final ServicesSoftwareVersion version;
    protected final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    protected FakeTssBaseService tssBaseService;

    protected AbstractEmbeddedHedera(@NonNull final EmbeddedNode node) {
        requireNonNull(node);
        addressBook = loadAddressBook(node.getExternalPath(ADDRESS_BOOK));
        roster = buildRoster(addressBook);
        nodeIds = stream(spliteratorUnknownSize(addressBook.iterator(), 0), false)
                .collect(toMap(AbstractEmbeddedHedera::accountIdOf, Address::getNodeId));
        accountIds = stream(spliteratorUnknownSize(addressBook.iterator(), 0), false)
                .collect(toMap(Address::getNodeId, address -> parseAccount(address.getMemo())));
        defaultNodeId = addressBook.getNodeId(0);
        defaultNodeAccountId = fromPbj(accountIds.get(defaultNodeId));
        hedera = new Hedera(
                ConstructableRegistry.getInstance(),
                FakeServicesRegistry.FACTORY,
                new FakeServiceMigrator(),
                this::now,
                appContext -> {
                    this.tssBaseService = new FakeTssBaseService(appContext);
                    return tssBaseService;
                },
                DiskStartupNetworks::new,
                NodeId.of(0L));
        version = (ServicesSoftwareVersion) hedera.getSoftwareVersion();
        blockStreamEnabled = hedera.isBlockStreamEnabled();
        Runtime.getRuntime().addShutdownHook(new Thread(executorService::shutdownNow));
    }

    @Override
    public void start() {
        final Configuration configuration =
                ConfigurationBuilder.create().autoDiscoverExtensions().build();

        hedera.initializeStatesApi(
                state, fakePlatform().getContext().getMetrics(), GENESIS, addressBook, configuration);

        // TODO - remove this after https://github.com/hashgraph/hedera-services/issues/16552 is done
        // and we are running all CI tests with the Roster lifecycle enabled
        final var writableStates = state.getWritableStates(PlatformStateService.NAME);
        final WritableSingletonState<PlatformState> platformState = writableStates.getSingleton(PLATFORM_STATE_KEY);
        final var currentState = requireNonNull(platformState.get());
        platformState.put(currentState
                .copyBuilder()
                .addressBook(toPbjAddressBook(addressBook))
                .build());
        ((CommittableWritableStates) writableStates).commit();
        if (!hedera.isRosterLifecycleEnabled()) {
            final var writableRosterStates = state.getWritableStates(RosterService.NAME);
            final WritableRosterStore writableRosterStore = new WritableRosterStore(writableRosterStates);
            writableRosterStore.putActiveRoster(buildRoster(addressBook), 0);
            ((CommittableWritableStates) writableRosterStates).commit();
        }
        // --- end of temporary code block ---

        hedera.setInitialStateHash(FAKE_START_OF_STATE_HASH);
        hedera.onStateInitialized(state, fakePlatform(), GENESIS);
        hedera.init(fakePlatform(), defaultNodeId);
        fakePlatform().start();
        fakePlatform().notifyListeners(ACTIVE_NOTIFICATION);
    }

    @Override
    public Hedera hedera() {
        return hedera;
    }

    @Override
    public void stop() {
        fakePlatform().notifyListeners(FREEZE_COMPLETE_NOTIFICATION);
        executorService.shutdownNow();
    }

    @Override
    public FakeState state() {
        return state;
    }

    @Override
    public FakeTssBaseService tssBaseService() {
        return tssBaseService;
    }

    @Override
    public SoftwareVersion version() {
        return version;
    }

    @Override
    public Timestamp nextValidStart() {
        var candidateNano = nextNano.getAndIncrement();
        if (candidateNano >= NANOS_IN_A_SECOND) {
            candidateNano = 0;
            nextNano.set(1);
        }
        return Timestamp.newBuilder()
                .setSeconds(now().getEpochSecond() - validStartOffsetSecs())
                .setNanos(candidateNano)
                .build();
    }

    @Override
    public Response send(
            @NonNull final Query query, @NonNull final AccountID nodeAccountId, final boolean asNodeOperator) {
        requireNonNull(query);
        requireNonNull(nodeAccountId);
        if (!defaultNodeAccountId.equals(nodeAccountId) && !isFree(query)) {
            // It's possible this was intentional, but make a little noise to remind test author this happens
            log.warn("All paid queries get INVALID_NODE_ACCOUNT for non-default nodes in embedded mode");
        }
        final var responseBuffer = BufferedData.allocate(MAX_QUERY_RESPONSE_SIZE);
        if (asNodeOperator) {
            hedera.operatorQueryWorkflow().handleQuery(Bytes.wrap(query.toByteArray()), responseBuffer);
        } else {
            hedera.queryWorkflow().handleQuery(Bytes.wrap(query.toByteArray()), responseBuffer);
        }
        return parseQueryResponse(responseBuffer);
    }

    @Override
    public TransactionResponse submit(
            @NonNull final Transaction transaction,
            @NonNull final AccountID nodeAccountId,
            @NonNull final SyntheticVersion syntheticVersion) {
        if (defaultNodeAccountId.equals(nodeAccountId)) {
            assertCurrent(syntheticVersion);
        }
        return submit(
                transaction,
                nodeAccountId,
                switch (syntheticVersion) {
                    case PAST -> EARLIER_SEMVER;
                    case PRESENT -> version.getPbjSemanticVersion();
                    case FUTURE -> LATER_SEMVER;
                });
    }

    /**
     * If block stream is enabled, notify the block stream manager of the state hash at the end of the round.
     * @param roundNumber the round number
     */
    protected void notifyBlockStreamManagerIfEnabled(final long roundNumber) {
        if (blockStreamEnabled) {
            hedera.blockStreamManager().notify(new StateHashedNotification(roundNumber, FAKE_START_OF_STATE_HASH));
        }
    }

    protected abstract TransactionResponse submit(
            @NonNull Transaction transaction, @NonNull AccountID nodeAccountId, @NonNull SemanticVersion version);

    /**
     * Returns the number of seconds to offset the next valid start time.
     * @return the number of seconds to offset the next valid start time
     */
    protected abstract long validStartOffsetSecs();

    /**
     * Returns the fake platform to start and stop.
     *
     * @return the fake platform
     */
    protected abstract AbstractFakePlatform fakePlatform();

    /**
     * Fails fast if somehow a user tries to manipulate the version when submitting to the default node account.
     *
     * @param syntheticVersion the synthetic version
     */
    private void assertCurrent(@NonNull final SyntheticVersion syntheticVersion) {
        if (syntheticVersion != SyntheticVersion.PRESENT) {
            throw new UnsupportedOperationException("Event version used at ingest by default node is always PRESENT");
        }
    }

    protected static TransactionResponse parseTransactionResponse(@NonNull final BufferedData responseBuffer) {
        try {
            return TransactionResponse.parseFrom(AbstractEmbeddedHedera.usedBytesFrom(responseBuffer));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static Response parseQueryResponse(@NonNull final BufferedData responseBuffer) {
        try {
            return Response.parseFrom(AbstractEmbeddedHedera.usedBytesFrom(responseBuffer));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static void warnOfSkippedIngestChecks(
            @NonNull final AccountID nodeAccountId, @NonNull final NodeId nodeId) {
        requireNonNull(nodeAccountId);
        requireNonNull(nodeId);
        // Bypass ingest for any other node, but make a little noise to remind test author this happens
        log.warn("Bypassing ingest checks for transaction to node{} (0.0.{})", nodeId, nodeAccountId.getAccountNum());
    }

    private static boolean isFree(@NonNull final Query query) {
        return query.hasCryptogetAccountBalance() || query.hasTransactionGetReceipt();
    }

    private static byte[] usedBytesFrom(@NonNull final BufferedData responseBuffer) {
        final byte[] bytes = new byte[Math.toIntExact(responseBuffer.position())];
        responseBuffer.resetPosition();
        responseBuffer.readBytes(bytes);
        return bytes;
    }

    private static AddressBook loadAddressBook(@NonNull final Path path) {
        requireNonNull(path);
        final var configFile = LegacyConfigPropertiesLoader.loadConfigFile(path.toAbsolutePath());
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

    private static AccountID accountIdOf(@NonNull final Address address) {
        return fromPbj(parseAccount(address.getMemo()));
    }
}
