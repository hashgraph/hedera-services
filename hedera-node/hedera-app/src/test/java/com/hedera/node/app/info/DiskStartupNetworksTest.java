/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.info.DiskStartupNetworks.fromLegacyAddressBook;
import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_KEY;
import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_STATES_KEY;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static com.hedera.node.app.workflows.standalone.TransactionExecutorsTest.FAKE_NETWORK_INFO;
import static com.hedera.node.app.workflows.standalone.TransactionExecutorsTest.NO_OP_METRICS;
import static com.swirlds.platform.state.service.PlatformStateService.PLATFORM_STATE_SERVICE;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fixtures.state.FakeServiceMigrator;
import com.hedera.node.app.fixtures.state.FakeServicesRegistry;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.info.DiskStartupNetworks.InfoType;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;
import com.hedera.node.app.tss.TssBaseServiceImpl;
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
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.spi.CommittableWritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
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
    private static final long ROUND_NO = 666L;

    private static Network NETWORK;

    @BeforeAll
    static void setupAll() throws IOException, ParseException {
        try (final var fin = DiskStartupNetworks.class.getClassLoader().getResourceAsStream("bootstrap/network.json")) {
            NETWORK = Network.JSON.parse(new ReadableStreamingData(requireNonNull(fin)));
        }
    }

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private StartupNetworks startupNetworks;

    @TempDir
    Path tempDir;

    private DiskStartupNetworks subject;

    @BeforeEach
    void setUp() {
        subject = new DiskStartupNetworks(configProvider);
    }

    @Test
    void throwsOnMissingGenesisNetwork() {
        givenConfig();
        assertThatThrownBy(() -> subject.genesisNetworkOrThrow(DEFAULT_CONFIG))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsOnMissingMigrationNetwork() {
        final var config = givenConfig();
        assertThatThrownBy(() -> subject.migrationNetworkOrThrow(config)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findsAvailableGenesisNetwork() throws IOException {
        givenConfig();
        putJsonAt(GENESIS_NETWORK_JSON, WithTssState.NO);
        final var network = subject.genesisNetworkOrThrow(DEFAULT_CONFIG);
        assertThat(network).isEqualTo(NETWORK);
    }

    @Test
    void findsAvailableMigrationNetwork() throws IOException {
        givenConfig();
        putJsonAt(OVERRIDE_NETWORK_JSON, WithTssState.YES);
        final var network = subject.migrationNetworkOrThrow(configProvider.getConfiguration());
        assertThat(network).isEqualTo(NETWORK);
    }

    @Test
    void computesFromLegacyAddressBook() {
        final int n = 3;
        final var legacyBook = new AddressBook(IntStream.range(0, n)
                .mapToObj(i -> new Address(
                        NodeId.of(i),
                        "" + i,
                        "node" + (i + 1),
                        1L,
                        "localhost",
                        i + 1,
                        "127.0.0.1",
                        i + 2,
                        null,
                        null,
                        "0.0." + (i + 3)))
                .toList());
        final var network = fromLegacyAddressBook(legacyBook);
        for (int i = 0; i < n; i++) {
            final var rosterEntry = network.nodeMetadata().get(i).rosterEntryOrThrow();
            assertThat(rosterEntry.nodeId()).isEqualTo(i);
            assertThat(rosterEntry.gossipEndpoint().getFirst().ipAddressV4())
                    .isEqualTo(Bytes.wrap(new byte[] {127, 0, 0, 1}));
            assertThat(rosterEntry.gossipEndpoint().getLast().domainName()).isEqualTo("localhost");
        }
    }

    @Test
    void archivesGenesisNetworks() throws IOException {
        givenConfig();
        putJsonAt(GENESIS_NETWORK_JSON, WithTssState.NO);
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
        putJsonAt(OVERRIDE_NETWORK_JSON, WithTssState.YES);
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
        putJsonAt(ROUND_NO + File.separator + OVERRIDE_NETWORK_JSON, WithTssState.YES);
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
        putJsonAt(OVERRIDE_NETWORK_JSON, WithTssState.YES);

        final var maybeOverrideNetwork = subject.overrideNetworkFor(ROUND_NO, DEFAULT_CONFIG);
        assertThat(maybeOverrideNetwork).isPresent();
        final var overrideNetwork = maybeOverrideNetwork.orElseThrow();
        assertThat(overrideNetwork).isEqualTo(NETWORK);

        subject.setOverrideRound(ROUND_NO);
        final var unscopedOverrideJson = tempDir.resolve(OVERRIDE_NETWORK_JSON);
        assertThat(Files.exists(unscopedOverrideJson)).isFalse();
        final var scopedOverrideJson = tempDir.resolve(+ROUND_NO + File.separator + OVERRIDE_NETWORK_JSON);
        assertThat(Files.exists(scopedOverrideJson)).isTrue();

        final var maybeRepeatedOverrideNetwork = subject.overrideNetworkFor(ROUND_NO, DEFAULT_CONFIG);
        assertThat(maybeRepeatedOverrideNetwork).isPresent();
        final var repeatedOverrideNetwork = maybeRepeatedOverrideNetwork.orElseThrow();
        assertThat(repeatedOverrideNetwork).isEqualTo(NETWORK);

        final var maybeOverrideNetworkAfterRound = subject.overrideNetworkFor(ROUND_NO + 1, DEFAULT_CONFIG);
        assertThat(maybeOverrideNetworkAfterRound).isEmpty();
    }

    @Test
    void writesExpectedStateInfo() throws IOException, ParseException {
        final var state = stateContainingInfoFrom(NETWORK);
        final var loc = tempDir.resolve("reproduced-network.json");
        DiskStartupNetworks.writeNetworkInfo(state, loc, EnumSet.allOf(InfoType.class));
        try (final var fin = Files.newInputStream(loc)) {
            final var network = Network.JSON.parse(new ReadableStreamingData(fin));
            Assertions.assertThat(network).isEqualTo(NETWORK);
        }
    }

    private enum WithTssState {
        YES,
        NO
    }

    private void putJsonAt(@NonNull final String fileName, @NonNull final WithTssState withTssState)
            throws IOException {
        final var loc = tempDir.resolve(fileName);
        try (final var fout = Files.newOutputStream(loc)) {
            Network.JSON.write(NETWORK, new WritableStreamingData(fout));
        }
    }

    private State stateContainingInfoFrom(@NonNull final Network network) {
        final var state = new FakeState();
        final var servicesRegistry = new FakeServicesRegistry();
        final var tssBaseService = new TssBaseServiceImpl();
        PLATFORM_STATE_SERVICE.setAppVersionFn(ServicesSoftwareVersion::from);
        PLATFORM_STATE_SERVICE.setDiskAddressBook(new AddressBook());
        Set.of(
                        tssBaseService,
                        PLATFORM_STATE_SERVICE,
                        new EntityIdService(),
                        new RosterService(roster -> true, () -> state),
                        new AddressBookServiceImpl())
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
        rosterState.put(new RosterState(Bytes.EMPTY, List.of(new RoundRosterPair(0L, currentRosterHash))));
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

    private Configuration givenConfig() {
        final var config = HederaTestConfigBuilder.create()
                .withValue("networkAdmin.upgradeSysFilesLoc", tempDir.toString())
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(config, 123L));
        return config;
    }
}
