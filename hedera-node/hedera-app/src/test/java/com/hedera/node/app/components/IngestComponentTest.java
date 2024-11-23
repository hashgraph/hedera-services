/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.components;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.spi.AppContext.Gossip.UNAVAILABLE_GOSSIP;
import static com.hedera.node.app.workflows.standalone.TransactionExecutors.DEFAULT_NODE_INFO;
import static com.swirlds.platform.system.address.AddressBookUtils.endpointFor;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.DaggerHederaInjectionComponent;
import com.hedera.node.app.HederaInjectionComponent;
import com.hedera.node.app.blocks.InitialStateHash;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.impl.KVStateChangeListener;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.signature.AppSignatureVerifier;
import com.hedera.node.app.signature.impl.SignatureExpanderImpl;
import com.hedera.node.app.signature.impl.SignatureVerifierImpl;
import com.hedera.node.app.spi.throttle.Throttle;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.handlers.TssHandlers;
import com.hedera.node.app.tss.handlers.TssMessageHandler;
import com.hedera.node.app.tss.handlers.TssShareSignatureHandler;
import com.hedera.node.app.tss.handlers.TssVoteHandler;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestComponentTest {

    @Mock
    private Platform platform;

    @Mock
    private TssBaseService tssBaseService;

    @Mock
    private TssMessageHandler tssMessageHandler;

    @Mock
    private TssVoteHandler tssVoteHandler;

    @Mock
    private TssShareSignatureHandler tssShareSignatureHandler;

    @Mock
    private Throttle.Factory throttleFactory;

    @Mock
    private StartupNetworks startupNetworks;

    private HederaInjectionComponent app;

    @BeforeEach
    void setUp() {
        final Configuration configuration = HederaTestConfigBuilder.createConfig();
        final PlatformContext platformContext = mock(PlatformContext.class);
        final Metrics metrics = new NoOpMetrics();
        lenient().when(platformContext.getConfiguration()).thenReturn(configuration);

        final var selfNodeInfo = new NodeInfoImpl(
                1L,
                AccountID.newBuilder().accountNum(1001).build(),
                10,
                List.of(endpointFor("127.0.0.1", 50211), endpointFor("127.0.0.1", 23456)),
                Bytes.wrap("cert7"));

        final var configProvider = new ConfigProviderImpl(false);
        final var appContext = new AppContextImpl(
                InstantSource.system(),
                new AppSignatureVerifier(
                        DEFAULT_CONFIG.getConfigData(HederaConfig.class),
                        new SignatureExpanderImpl(),
                        new SignatureVerifierImpl(CryptographyHolder.get())),
                UNAVAILABLE_GOSSIP,
                () -> configuration,
                () -> DEFAULT_NODE_INFO,
                throttleFactory);
        given(tssBaseService.tssHandlers())
                .willReturn(new TssHandlers(tssMessageHandler, tssVoteHandler, tssShareSignatureHandler));
        app = DaggerHederaInjectionComponent.builder()
                .configProviderImpl(configProvider)
                .bootstrapConfigProviderImpl(new BootstrapConfigProviderImpl())
                .fileServiceImpl(new FileServiceImpl())
                .contractServiceImpl(new ContractServiceImpl(appContext))
                .scheduleService(new ScheduleServiceImpl())
                .initTrigger(InitTrigger.GENESIS)
                .platform(platform)
                .crypto(CryptographyHolder.get())
                .self(selfNodeInfo)
                .maxSignedTxnSize(1024)
                .currentPlatformStatus(() -> PlatformStatus.ACTIVE)
                .servicesRegistry(mock(ServicesRegistry.class))
                .instantSource(InstantSource.system())
                .softwareVersion(mock(SemanticVersion.class))
                .metrics(metrics)
                .kvStateChangeListener(new KVStateChangeListener())
                .boundaryStateChangeListener(new BoundaryStateChangeListener())
                .migrationStateChanges(List.of())
                .tssBaseService(tssBaseService)
                .initialStateHash(new InitialStateHash(completedFuture(Bytes.EMPTY), 0))
                .networkInfo(mock(NetworkInfo.class))
                .startupNetworks(startupNetworks)
                .throttleFactory(throttleFactory)
                .build();

        final var state = new FakeState();
        state.addService(RecordCacheService.NAME, Map.of("TransactionRecordQueue", new ArrayDeque<String>()));
        state.addService(RecordCacheService.NAME, Map.of("TransactionReceiptQueue", new ArrayDeque<String>()));
        app.workingStateAccessor().setState(state);
    }

    @Test
    void objectGraphRootsAreAvailable() {
        final IngestInjectionComponent subject =
                app.ingestComponentFactory().get().create();

        assertNotNull(subject.ingestWorkflow());
    }
}
