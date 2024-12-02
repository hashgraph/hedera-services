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

package com.swirlds.platform.state.service.schemas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.internal.network.Network;
import com.hedera.node.internal.network.NodeMetadata;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.state.service.WritablePlatformStateStore;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V057PlatformStateSchemaTest {
    private static final Network NETWORK = Network.newBuilder()
            .nodeMetadata(NodeMetadata.newBuilder()
                    .rosterEntry(RosterEntry.newBuilder()
                            .nodeId(1L)
                            .gossipCaCertificate(
                                    Bytes.fromHex(
                                            "308203b130820219a003020102020900ad394d2f0a65e6f5300d06092a864886f70d01010c05003017311530130603550403130c732d7a77396a6566793079763020170d3030303130313030303030305a180f32313030303130313030303030305a3017311530130603550403130c732d7a77396a656679307976308201a2300d06092a864886f70d01010105000382018f003082018a0282018100c1a0ff5d2372b53d12d12bb87dd03f5e3427e0cee1d3c898bbd320c4b3dd17257944ea39a07f5344d9abfcdd50214072f1bbc12173fe7933d032c7d210734cc92d24be22b44cf50c2aa06f19bcd75180dc3e8dedd5ffcac02bf98721df9c3e79f20e9942cac9328b99160afea44d42c87b0147f3f29567085ed3f841dbe37aba35a2c5446bc638c62c703a6f680fa0601bfe7c6254e9fe2f471670ecdcca26128716a08f4141595ec0c4ac7ae589f37deede17480ecc1500f88335d0e33929725e8e4e775f3e4aa44c867bc86d3bf6d7165a4b766dd4ceb622221634a0a3d82840800b5b3e540640ea2f8c5749c3a6a0e0c474515c3f0ed9aadab8f84423a8954fd7f4e40b73125aeced4f791dba5052e3f5b3191a430f9b2dd30e4071cc54280c830da0d1e0dd54300c243ef08d9f81b3a90373f10910b6f4975bb2d861273993221e42b82b5af823267f79de90a7221129f0423724f9208a4ca15a73458c555e08e015db9d77c884acacaf4971d3854ea7bbdd9cfaf49df852c11473e96fa10203010001300d06092a864886f70d01010c05000382018100455e8d6c1b276d3d20a4b1ccc0abbbb36460cd985612d1068aab8cd5a479877f524c808c44469d3f17a752d7ce24d6e1536d5f02b8788890f6249c135ed05583126b6d38f7bf2d42c5a404f34379387d659eff5ff8a6ac1938254c2a3cee6abbbaca7b8e7069f7da8d5ce157a3c40bc0220abd05d8f54f0ac4aba3757a076ba14f598f1f835e566f71a50f933af979501499d959d356e71c5e954fdb0428578115fa540417b32156861c0f7960fa1e0473c76b2d579fbc30aa7ce718c7c413811b024f66d0e7e3350b30bd39a74f1f325818e5d26eececec78108bc77d55615b1568fb9b74e7567679606541d36f4f44c2bd07f5adb81c384d4b8ea1a287fbd356278344ec2582f040187f2f241ff812d54861754a47838b9cbe94f9d3e9333183c4f651a2e2ba3f5dcd77ff0560db17cb0d3481718d68aaafa076c6612674d3c264d42352811c2510a418987d1fba46ccaf5fe5d3b579fe002c106ffd4cd83ff0d0e16c9d92694a1764637d6fd2298fc1389c10de4e43b7fd1738d3acc13660"))
                            .build())
                    .build())
            .build();

    private static final Roster ROSTER = new Roster(NETWORK.nodeMetadata().stream()
            .map(NodeMetadata::rosterEntryOrThrow)
            .toList());

    private static final SemanticVersion THEN =
            SemanticVersion.newBuilder().major(7).build();

    @Mock
    private Supplier<Roster> activeRosterSupplier;

    @Mock
    private Supplier<SoftwareVersion> appVersionSupplier;

    @Mock
    private MigrationContext migrationContext;

    @Mock
    private Configuration configuration;

    @Mock
    private WritableStates writableStates;

    @Mock
    private Function<WritableStates, WritablePlatformStateStore> platformStateStoreFactory;

    @Mock
    private WritablePlatformStateStore platformStateStore;

    @Mock
    private StartupNetworks startupNetworks;

    private V057PlatformStateSchema schema;

    @BeforeEach
    void setUp() {
        schema = new V057PlatformStateSchema(activeRosterSupplier, appVersionSupplier, platformStateStoreFactory);
    }

    @Test
    void noOpIfNotUsingRosterLifecycle() {
        givenContextWith(CurrentVersion.NA, RosterLifecycle.OFF, AvailableNetwork.NONE);

        schema.restart(migrationContext);

        verify(migrationContext, never()).newStates();
    }

    @Test
    void platformStateIsUpdatedOnGenesis() {
        givenContextWith(CurrentVersion.NA, RosterLifecycle.ON, AvailableNetwork.GENESIS);

        schema.restart(migrationContext);

        verify(platformStateStore, times(1)).bulkUpdate(any());
    }

    @Test
    void platformStateNotUpdatedIfNotUpgradeBoundary() {
        givenContextWith(CurrentVersion.OLD, RosterLifecycle.ON, AvailableNetwork.NONE);
        given(migrationContext.previousVersion()).willReturn(THEN);

        schema.restart(migrationContext);

        verify(platformStateStore, never()).bulkUpdate(any());
    }

    @Test
    void platformStateIsUpdatedOnUpgradeBoundary() {
        givenContextWith(CurrentVersion.NEW, RosterLifecycle.ON, AvailableNetwork.NONE);
        given(migrationContext.previousVersion()).willReturn(THEN);
        given(activeRosterSupplier.get()).willReturn(ROSTER);

        schema.restart(migrationContext);

        verify(platformStateStore, times(1)).bulkUpdate(any());
    }

    private enum CurrentVersion {
        NA,
        OLD,
        NEW,
    }

    private enum RosterLifecycle {
        ON,
        OFF
    }

    private enum AvailableNetwork {
        GENESIS,
        NONE
    }

    private void givenContextWith(
            @NonNull final CurrentVersion currentVersion,
            @NonNull final RosterLifecycle rosterLifecycle,
            @NonNull final AvailableNetwork availableNetwork) {
        switch (currentVersion) {
            case NA -> {
                // No-op
            }
            case OLD -> given(appVersionSupplier.get()).willReturn(new BasicSoftwareVersion(7));
            case NEW -> given(appVersionSupplier.get()).willReturn(new BasicSoftwareVersion(42));
        }

        given(migrationContext.appConfig()).willReturn(configuration);
        given(configuration.getConfigData(AddressBookConfig.class))
                .willReturn(new AddressBookConfig(
                        true,
                        false,
                        null,
                        50,
                        switch (rosterLifecycle) {
                            case ON -> true;
                            case OFF -> false;
                        }));

        if (rosterLifecycle == RosterLifecycle.ON) {
            given(migrationContext.newStates()).willReturn(writableStates);
            given(platformStateStoreFactory.apply(writableStates)).willReturn(platformStateStore);
            given(migrationContext.startupNetworks()).willReturn(startupNetworks);
        }

        if (availableNetwork == AvailableNetwork.GENESIS) {
            given(migrationContext.isGenesis()).willReturn(true);
            given(startupNetworks.genesisNetworkOrThrow()).willReturn(NETWORK);
        }
    }
}
