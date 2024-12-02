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

package com.hedera.node.app.info;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.info.DiskStartupNetworks.ARCHIVE;
import static com.hedera.node.app.info.DiskStartupNetworks.GENESIS_NETWORK_JSON;
import static com.hedera.node.app.info.DiskStartupNetworks.OVERRIDE_NETWORK_JSON;
import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_KEY;
import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_STATES_KEY;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static com.hedera.node.app.spi.AppContext.Gossip.UNAVAILABLE_GOSSIP;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_VOTE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_ENCRYPTION_KEYS_KEY;
import static com.hedera.node.app.workflows.standalone.TransactionExecutorsTest.FAKE_NETWORK_INFO;
import static com.hedera.node.app.workflows.standalone.TransactionExecutorsTest.NO_OP_METRICS;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssStatus;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssEncryptionKeyTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fixtures.state.FakeServiceMigrator;
import com.hedera.node.app.fixtures.state.FakeServicesRegistry;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.TssBaseServiceImpl;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.schemas.V0580TssBaseSchema;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.node.internal.network.Network;
import com.hedera.node.internal.network.NodeMetadata;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.service.ReadableRosterStoreImpl;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.spi.CommittableWritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiskStartupNetworksTest {
    private static final int FAKE_NETWORK_SIZE = 4;
    private static final long NODE_ID = 0L;
    private static final long ROUND_NO = 666L;
    private static final Bytes EXPECTED_LEDGER_ID = Bytes.fromBase64("Lw==");
    private static final Comparator<TssMessageTransactionBody> TSS_MESSAGE_COMPARATOR =
            Comparator.comparingLong(TssMessageTransactionBody::shareIndex);

    private static Network networkWithTssKeys;
    private static Network networkWithoutTssKeys;

    @BeforeAll
    static void setupAll() throws IOException, ParseException {
        try (final var fin = DiskStartupNetworks.class.getClassLoader().getResourceAsStream("bootstrap/network.json")) {
            networkWithTssKeys = Network.JSON.parse(new ReadableStreamingData(requireNonNull(fin)));
            networkWithTssKeys = networkWithTssKeys
                    .copyBuilder()
                    .tssMessages(networkWithTssKeys.tssMessages().stream()
                            .sorted(TSS_MESSAGE_COMPARATOR)
                            .toList())
                    .build();
            networkWithoutTssKeys = networkWithTssKeys
                    .copyBuilder()
                    .tssMessages(emptyList())
                    .ledgerId(Bytes.EMPTY)
                    .build();
        }
    }

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private TssBaseService tssBaseService;

    @Mock
    private AppContext appContext;

    @Mock
    private TssLibrary tssLibrary;

    @Mock
    private StartupNetworks startupNetworks;

    @TempDir
    Path tempDir;

    private DiskStartupNetworks subject;

    @BeforeEach
    void setUp() {
        subject = new DiskStartupNetworks(configProvider, tssBaseService);
    }

    @Test
    void throwsOnMissingGenesisNetwork() {
        givenConfig();
        assertThatThrownBy(() -> subject.genesisNetworkOrThrow()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsOnMissingMigrationNetwork() {
        givenConfig();
        assertThatThrownBy(() -> subject.migrationNetworkOrThrow()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findsAvailableGenesisNetwork() throws IOException {
        givenConfig();
        putJsonAt(GENESIS_NETWORK_JSON, WithTssKeys.NO);
        final var network = subject.genesisNetworkOrThrow();
        assertThat(network).isEqualTo(networkWithoutTssKeys);
    }

    @Test
    void findsAvailableMigrationNetwork() throws IOException {
        givenConfig();
        givenValidTssMessages();
        putJsonAt(OVERRIDE_NETWORK_JSON, WithTssKeys.YES);
        final var network = subject.migrationNetworkOrThrow();
        assertThat(network).isEqualTo(networkWithTssKeys);
    }

    @Test
    void archivesGenesisNetworks() throws IOException {
        givenConfig();
        putJsonAt(GENESIS_NETWORK_JSON, WithTssKeys.NO);
        final var genesisJson = tempDir.resolve(GENESIS_NETWORK_JSON);

        assertThat(Files.exists(genesisJson)).isTrue();

        subject.archiveStartupNetworks();
        assertThatNoException().isThrownBy(() -> subject.archiveStartupNetworks());

        assertThat(Files.exists(genesisJson)).isFalse();
        final var archivedGenesisJson = tempDir.resolve(ARCHIVE + File.separator + GENESIS_NETWORK_JSON);
        assertThat(Files.exists(archivedGenesisJson)).isTrue();
    }

    @Test
    void archivesUnscopedOverrideNetwork() throws IOException {
        givenConfig();
        putJsonAt(OVERRIDE_NETWORK_JSON, WithTssKeys.YES);
        final var overrideJson = tempDir.resolve(OVERRIDE_NETWORK_JSON);

        assertThat(Files.exists(overrideJson)).isTrue();

        subject.archiveStartupNetworks();

        assertThat(Files.exists(overrideJson)).isFalse();
        final var archivedGenesisJson = tempDir.resolve(ARCHIVE + File.separator + OVERRIDE_NETWORK_JSON);
        assertThat(Files.exists(archivedGenesisJson)).isTrue();
    }

    @Test
    void archivesScopedOverrideNetwork() throws IOException {
        givenConfig();
        Files.createDirectory(tempDir.resolve("" + ROUND_NO));
        putJsonAt(ROUND_NO + File.separator + OVERRIDE_NETWORK_JSON, WithTssKeys.YES);
        final var overrideJson = tempDir.resolve(ROUND_NO + File.separator + OVERRIDE_NETWORK_JSON);

        assertThat(Files.exists(overrideJson)).isTrue();

        subject.archiveStartupNetworks();

        assertThat(Files.exists(overrideJson)).isFalse();
        final var archivedGenesisJson =
                tempDir.resolve(ARCHIVE + File.separator + ROUND_NO + File.separator + OVERRIDE_NETWORK_JSON);
        assertThat(Files.exists(archivedGenesisJson)).isTrue();
    }

    @Test
    void overrideNetworkOnlyStillAvailableAtSameRound() throws IOException {
        givenConfig();
        putJsonAt(OVERRIDE_NETWORK_JSON, WithTssKeys.YES);
        givenValidTssMessages();

        final var maybeOverrideNetwork = subject.overrideNetworkFor(ROUND_NO);
        assertThat(maybeOverrideNetwork).isPresent();
        final var overrideNetwork = maybeOverrideNetwork.orElseThrow();
        assertThat(overrideNetwork).isEqualTo(networkWithTssKeys);

        subject.setOverrideRound(ROUND_NO);
        final var unscopedOverrideJson = tempDir.resolve(OVERRIDE_NETWORK_JSON);
        assertThat(Files.exists(unscopedOverrideJson)).isFalse();
        final var scopedOverrideJson = tempDir.resolve(+ROUND_NO + File.separator + OVERRIDE_NETWORK_JSON);
        assertThat(Files.exists(scopedOverrideJson)).isTrue();

        final var maybeRepeatedOverrideNetwork = subject.overrideNetworkFor(ROUND_NO);
        assertThat(maybeRepeatedOverrideNetwork).isPresent();
        final var repeatedOverrideNetwork = maybeRepeatedOverrideNetwork.orElseThrow();
        assertThat(repeatedOverrideNetwork).isEqualTo(networkWithTssKeys);

        final var maybeOverrideNetworkAfterRound = subject.overrideNetworkFor(ROUND_NO + 1);
        assertThat(maybeOverrideNetworkAfterRound).isEmpty();
    }

    @Test
    void writesExpectedStateInfo() throws IOException, ParseException {
        final var state = stateContainingInfoFrom(networkWithTssKeys);
        final var loc = tempDir.resolve("reproduced-network.json");
        DiskStartupNetworks.writeNetworkInfo(state, loc);
        try (final var fin = Files.newInputStream(loc)) {
            var network = Network.JSON.parse(new ReadableStreamingData(fin));
            network = network.copyBuilder()
                    .tssMessages(network.tssMessages().stream()
                            .sorted(TSS_MESSAGE_COMPARATOR)
                            .toList())
                    .build();
            Assertions.assertThat(network).isEqualTo(networkWithTssKeys);
        }
    }

    private enum WithTssKeys {
        YES,
        NO
    }

    private void givenValidTssMessages() {
        given(tssBaseService.ledgerIdFrom(any(), any())).willReturn(EXPECTED_LEDGER_ID);
    }

    private void putJsonAt(@NonNull final String fileName, @NonNull final WithTssKeys withTssKeys) throws IOException {
        final var loc = tempDir.resolve(fileName);
        try (final var fout = Files.newOutputStream(loc)) {
            Network.JSON.write(
                    withTssKeys == WithTssKeys.YES ? networkWithTssKeys : networkWithoutTssKeys,
                    new WritableStreamingData(fout));
        }
    }

    private State stateContainingInfoFrom(@NonNull final Network network) {
        final var state = new FakeState();
        final var servicesRegistry = new FakeServicesRegistry();
        given(appContext.gossip()).willReturn(UNAVAILABLE_GOSSIP);
        given(appContext.instantSource()).willReturn(InstantSource.system());
        final var tssBaseService = new TssBaseServiceImpl(
                appContext,
                ForkJoinPool.commonPool(),
                ForkJoinPool.commonPool(),
                tssLibrary,
                ForkJoinPool.commonPool(),
                NO_OP_METRICS);
        Set.of(tssBaseService, new EntityIdService(), new RosterService(roster -> true), new AddressBookServiceImpl())
                .forEach(servicesRegistry::register);
        final var migrator = new FakeServiceMigrator();
        final var bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
        migrator.doMigrations(
                state,
                servicesRegistry,
                null,
                new ServicesSoftwareVersion(
                        bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion()),
                new ConfigProviderImpl().getConfiguration(),
                DEFAULT_CONFIG,
                FAKE_NETWORK_INFO,
                NO_OP_METRICS,
                startupNetworks);
        addRosterInfo(state, network);
        addTssInfo(state, network);
        addAddressBookInfo(state, network);
        return state;
    }

    private void addRosterInfo(@NonNull final FakeState state, @NonNull final Network network) {
        final var writableStates = state.getWritableStates(RosterService.NAME);
        final var rosterEntries = network.nodeMetadata().stream()
                .map(NodeMetadata::rosterEntryOrThrow)
                .toList();
        final var currentRoster = new Roster(rosterEntries);
        final var currentRosterHash = RosterUtils.hash(currentRoster).getBytes();
        final var rosters = writableStates.<ProtoBytes, Roster>get(ROSTER_KEY);
        rosters.put(new ProtoBytes(currentRosterHash), currentRoster);
        final var rosterState = writableStates.<RosterState>getSingleton(ROSTER_STATES_KEY);
        rosterState.put(new RosterState(Bytes.EMPTY, List.of(new RoundRosterPair(1L, currentRosterHash))));
        ((CommittableWritableStates) writableStates).commit();
    }

    private void addAddressBookInfo(@NonNull final FakeState state, @NonNull final Network network) {
        final var writableStates = state.getWritableStates(AddressBookService.NAME);
        final var metadata =
                network.nodeMetadata().stream().map(NodeMetadata::nodeOrThrow).toList();
        final var nodes = writableStates.<EntityNumber, Node>get(NODES_KEY);
        metadata.forEach(node -> nodes.put(new EntityNumber(node.nodeId()), node));
        ((CommittableWritableStates) writableStates).commit();
    }

    private void addTssInfo(@NonNull final FakeState state, @NonNull final Network network) {
        final var rosterStore = new ReadableRosterStoreImpl(state.getReadableStates(RosterService.NAME));
        final var writableStates = state.getWritableStates(TssBaseService.NAME);
        final var sourceRosterHash =
                Optional.ofNullable(rosterStore.getPreviousRosterHash()).orElse(Bytes.EMPTY);
        final var targetRosterHash = requireNonNull(rosterStore.getCurrentRosterHash());

        final var bitSet = new BitSet();
        final var thresholdTssMessages = network.tssMessages();
        final var tssMessages = writableStates.<TssMessageMapKey, TssMessageTransactionBody>get(TSS_MESSAGE_MAP_KEY);
        for (int i = 0, n = thresholdTssMessages.size(); i < n; i++) {
            final var key = new TssMessageMapKey(targetRosterHash, i);
            tssMessages.put(key, thresholdTssMessages.get(i));
            bitSet.set(i);
        }
        ((CommittableWritableStates) writableStates).commit();

        final var tssVotes = writableStates.<TssVoteMapKey, TssVoteTransactionBody>get(TSS_VOTE_MAP_KEY);
        final var tssVote = Bytes.wrap(bitSet.toByteArray());
        for (int i = 0; i < FAKE_NETWORK_SIZE; i++) {
            final var key = new TssVoteMapKey(targetRosterHash, i);
            final var vote = new TssVoteTransactionBody(
                    sourceRosterHash, targetRosterHash, EXPECTED_LEDGER_ID, Bytes.EMPTY, tssVote);
            tssVotes.put(key, vote);
        }

        final var tssEncryptionKey =
                writableStates.<EntityNumber, TssEncryptionKeyTransactionBody>get(TSS_ENCRYPTION_KEYS_KEY);
        for (int i = 0; i < FAKE_NETWORK_SIZE; i++) {
            final var key = new EntityNumber(i);
            final var value = TssEncryptionKeyTransactionBody.newBuilder()
                    .publicTssEncryptionKey(Bytes.EMPTY)
                    .build();
            tssEncryptionKey.put(key, value);
        }

        final var tssStatus = writableStates.<TssStatus>getSingleton(V0580TssBaseSchema.TSS_STATUS_KEY);
        tssStatus.put(TssStatus.DEFAULT);
        ((CommittableWritableStates) writableStates).commit();
    }

    private void givenConfig() {
        final var config = HederaTestConfigBuilder.create()
                .withValue("networkAdmin.upgradeSysFilesLoc", tempDir.toString())
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(config, 123L));
    }
}
