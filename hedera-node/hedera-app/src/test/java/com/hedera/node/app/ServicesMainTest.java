/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app;

import static com.swirlds.platform.system.SystemExitCode.NODE_ADDRESS_MISMATCH;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.services.OrderedServiceMigrator;
import com.hedera.node.app.services.ServicesRegistryImpl;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.merkle.crypto.MerkleCryptographyFactory;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.builder.PlatformBuilder;
import com.swirlds.platform.builder.internal.StaticPlatformBuilder;
import com.swirlds.platform.config.legacy.ConfigurationException;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.address.AddressBookUtils;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.StartupStateUtils;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SystemExitUtils;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.util.BootstrapUtils;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class ServicesMainTest {
    private static final MockedStatic<LegacyConfigPropertiesLoader> legacyConfigPropertiesLoaderMockedStatic =
            mockStatic(LegacyConfigPropertiesLoader.class);
    private static final MockedStatic<BootstrapUtils> bootstrapUtilsMockedStatic = mockStatic(BootstrapUtils.class);

    @Mock(strictness = LENIENT)
    private LegacyConfigProperties legacyConfigProperties;

    @Mock(strictness = LENIENT)
    private AddressBook addressBook;

    @Mock(strictness = LENIENT)
    private DefaultMetricsProvider metricsProvider;

    @Mock(strictness = LENIENT)
    private Metrics metrics;

    @Mock(strictness = LENIENT)
    private FileSystemManager fileSystemManager;

    @Mock(strictness = LENIENT)
    private RecycleBin recycleBin;

    @Mock(strictness = LENIENT)
    private MerkleCryptography merkleCryptography;

    @Mock(strictness = LENIENT)
    private PlatformContext platformContext;

    @Mock(strictness = LENIENT)
    private PlatformBuilder platformBuilder;

    @Mock(strictness = LENIENT)
    private ReservedSignedState reservedSignedState;

    @Mock(strictness = LENIENT)
    private SignedState signedState;

    @Mock(strictness = LENIENT)
    private Platform platform;

    @Mock(strictness = LENIENT)
    private Hedera hedera;

    private final ServicesMain subject = new ServicesMain();

    // no local nodes specified but more than one match in address book
    @Test
    void hardExitOnTooManyLocalNodes() {
        withBadCommandLineArgs();
        String[] args = {};

        try (MockedStatic<SystemExitUtils> systemExitUtilsMockedStatic = mockStatic(SystemExitUtils.class)) {
            assertThatThrownBy(() -> ServicesMain.main(args)).isInstanceOf(ConfigurationException.class);

            systemExitUtilsMockedStatic.verify(() -> SystemExitUtils.exitSystem(NODE_ADDRESS_MISMATCH));
        }
    }

    // local node specified which does not match the address book
    @Test
    void hardExitOnNonMatchingNodeId() {
        withBadCommandLineArgs();
        String[] args = {"-local", "1234"}; // 1234 does not match anything in address book

        try (MockedStatic<SystemExitUtils> systemExitUtilsMockedStatic = mockStatic(SystemExitUtils.class)) {
            assertThatThrownBy(() -> ServicesMain.main(args)).isInstanceOf(ConfigurationException.class);

            systemExitUtilsMockedStatic.verify(() -> SystemExitUtils.exitSystem(NODE_ADDRESS_MISMATCH));
        }
    }

    // more than one local node specified which matches the address book
    @Test
    void hardExitOnTooManyMatchingNodes() {
        withBadCommandLineArgs();
        String[] args = {"-local", "1", "2"}; // both "1" and "2" match entries in address book

        try (MockedStatic<SystemExitUtils> systemExitUtilsMockedStatic = mockStatic(SystemExitUtils.class)) {
            systemExitUtilsMockedStatic
                    .when(() -> SystemExitUtils.exitSystem(any()))
                    .thenThrow(new UnsupportedOperationException());
            assertThatThrownBy(() -> ServicesMain.main(args)).isInstanceOf(UnsupportedOperationException.class);

            systemExitUtilsMockedStatic.verify(() -> SystemExitUtils.exitSystem(NODE_ADDRESS_MISMATCH));
        }
    }

    @Test
    void returnsSerializableVersion() {
        assertInstanceOf(ServicesSoftwareVersion.class, subject.getSoftwareVersion());
    }

    @Test
    void noopsAsExpected() {
        // expect:
        assertDoesNotThrow(subject::run);
    }

    @Test
    void createsNewMerkleStateRoot() {
        // expect:
        assertThat(subject.newMerkleStateRoot(), instanceOf(MerkleStateRoot.class));
    }

    @Test
    void initializesEverythingCorrectly() throws Exception {
        withCorrectCommandLineArgs();
        String[] args = {"-local", "1"};

        final var map = new HashMap<NodeId, KeysAndCerts>();
        final var keysAndCerts = mock(KeysAndCerts.class);
        map.put(new NodeId(1), keysAndCerts);

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        try (MockedStatic<CryptoStatic> cryptoMockedStatic = mockStatic(CryptoStatic.class);
                MockedStatic<StaticPlatformBuilder> staticPlatformBuilderMockedStatic =
                        mockStatic(StaticPlatformBuilder.class);
                MockedStatic<FileSystemManager> fileSystemManagerMockedStatic = mockStatic(FileSystemManager.class);
                MockedStatic<RecycleBin> recycleBinMockedStatic = mockStatic(RecycleBin.class);
                MockedStatic<MerkleCryptographyFactory> merkleCryptographyMockedStatic =
                        mockStatic(MerkleCryptographyFactory.class);
                MockedStatic<PlatformBuilder> platformBuilderMockedStatic = mockStatic(PlatformBuilder.class);
                MockedStatic<PlatformContext> platformContextMockedStatic = mockStatic(PlatformContext.class);
                MockedStatic<StartupStateUtils> startupStateUtilsMockedStatic = mockStatic(StartupStateUtils.class);
                MockedStatic<AddressBookUtils> addressbookUtilsMockedStatic = mockStatic(AddressBookUtils.class);
                MockedConstruction<Hedera> mockedConstruction = Mockito.mockConstruction(Hedera.class)) {
            cryptoMockedStatic
                    .when(() -> CryptoStatic.initNodeSecurity(any(), any()))
                    .thenReturn(map);
            platformBuilderMockedStatic
                    .when(() -> StaticPlatformBuilder.setupGlobalMetrics(any()))
                    .thenAnswer(invocation -> null);
            staticPlatformBuilderMockedStatic
                    .when(() -> StaticPlatformBuilder.getMetricsProvider())
                    .thenReturn(metricsProvider);
            fileSystemManagerMockedStatic
                    .when(() -> FileSystemManager.create(any()))
                    .thenReturn(fileSystemManager);
            recycleBinMockedStatic
                    .when(() -> RecycleBin.create(any(), any(), any(), any(), any(), any()))
                    .thenReturn(recycleBin);
            merkleCryptographyMockedStatic
                    .when(() -> MerkleCryptographyFactory.create(any(), any()))
                    .thenReturn(merkleCryptography);
            platformBuilderMockedStatic
                    .when(() -> PlatformBuilder.create(any(), any(), any(), any(), any()))
                    .thenReturn(platformBuilder);
            platformContextMockedStatic
                    .when(() -> PlatformContext.create(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(platformContext);
            startupStateUtilsMockedStatic
                    .when(() ->
                            StartupStateUtils.getInitialState(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(reservedSignedState);
            bootstrapUtilsMockedStatic
                    .when(() -> BootstrapUtils.detectSoftwareUpgrade(any(), any()))
                    .thenReturn(false);
            addressbookUtilsMockedStatic
                    .when(() -> AddressBookUtils.initializeAddressBook(any(), any(), any(), any(), any()))
                    .thenReturn(addressBook);
            new Hedera(
                    ConstructableRegistry.getInstance(),
                    ServicesRegistryImpl::new,
                    new OrderedServiceMigrator(),
                    InstantSource.system());
            final List<Hedera> hedera = mockedConstruction.constructed();

            given(metricsProvider.createGlobalMetrics()).willReturn(metrics);
            given(reservedSignedState.get()).willReturn(signedState);
            given(signedState.getRound()).willReturn(1L);
            given(signedState.getAddressBook()).willReturn(addressBook);
            given(platformBuilder.withPlatformContext(any())).willReturn(platformBuilder);
            given(platformBuilder.withAddressBook(any())).willReturn(platformBuilder);
            given(platformBuilder.withRoster(any())).willReturn(platformBuilder);
            given(platformBuilder.withKeysAndCerts(any())).willReturn(platformBuilder);
            given(platformBuilder.withConfiguration(any())).willReturn(platformBuilder);
            given(platformBuilder.build()).willReturn(platform);

            ServicesMain.main(args);
            verify(platformBuilder).withPlatformContext(platformContext);
            verify(platformBuilder).withAddressBook(addressBook);
            verify(platformBuilder).withRoster(Roster.newBuilder().build());
            verify(platform).start();
            verify(platformBuilder).build();
        }
    }

    private void withBadCommandLineArgs() {
        legacyConfigPropertiesLoaderMockedStatic
                .when(() -> LegacyConfigPropertiesLoader.loadConfigFile(any()))
                .thenReturn(legacyConfigProperties);

        List<NodeId> nodeIds = new ArrayList<>();
        nodeIds.add(new NodeId(1));
        nodeIds.add(new NodeId(2));

        bootstrapUtilsMockedStatic
                .when(() -> BootstrapUtils.getNodesToRun(any(), any()))
                .thenReturn(nodeIds);
    }

    private void withCorrectCommandLineArgs() {
        legacyConfigPropertiesLoaderMockedStatic
                .when(() -> LegacyConfigPropertiesLoader.loadConfigFile(any()))
                .thenReturn(legacyConfigProperties);

        List<NodeId> nodeIds = new ArrayList<>();
        nodeIds.add(new NodeId(1));
        lenient().when(legacyConfigProperties.getAddressBook()).thenReturn(addressBook);
        lenient().when(addressBook.getNodeIdSet()).thenReturn(new HashSet<>(List.of(new NodeId(1))));
        bootstrapUtilsMockedStatic
                .when(() -> BootstrapUtils.getNodesToRun(any(), any()))
                .thenReturn(nodeIds);
    }
}
