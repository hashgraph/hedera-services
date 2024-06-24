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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.DaggerHederaInjectionComponent;
import com.hedera.node.app.HederaInjectionComponent;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.congestion.ThrottleMultiplier;
import com.hedera.node.app.fees.congestion.UtilizationScaledThrottleMultiplier;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.info.SelfNodeInfoImpl;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.status.PlatformStatus;
import java.time.InstantSource;
import java.util.ArrayDeque;
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
    private PlatformContext platformContext;

    @Mock
    private Metrics metrics;

    @Mock
    UtilizationScaledThrottleMultiplier genericFeeMultiplier;

    @Mock
    ThrottleMultiplier gasFeeMultiplier;

    private HederaInjectionComponent app;

    @BeforeEach
    void setUp() {
        final Configuration configuration = HederaTestConfigBuilder.createConfig();
        final PlatformContext platformContext = mock(PlatformContext.class);
        lenient().when(platformContext.getConfiguration()).thenReturn(configuration);
        when(platform.getContext()).thenReturn(platformContext);

        final var selfNodeInfo = new SelfNodeInfoImpl(
                1L,
                AccountID.newBuilder().accountNum(1001).build(),
                10,
                "127.0.0.1",
                50211,
                "127.0.0.4",
                23456,
                "0123456789012345678901234567890123456789012345678901234567890123",
                "Node7",
                Bytes.wrap("cert7"),
                new HederaSoftwareVersion(
                        SemanticVersion.newBuilder().major(1).build(),
                        SemanticVersion.newBuilder().major(2).build(),
                        0));

        final var configProvider = new ConfigProviderImpl(false);
        app = DaggerHederaInjectionComponent.builder()
                .initTrigger(InitTrigger.GENESIS)
                .platform(platform)
                .crypto(CryptographyHolder.get())
                .configProvider(configProvider)
                .configProviderImpl(configProvider)
                .self(selfNodeInfo)
                .maxSignedTxnSize(1024)
                .currentPlatformStatus(() -> PlatformStatus.ACTIVE)
                .servicesRegistry(mock(ServicesRegistry.class))
                .instantSource(InstantSource.system())
                .softwareVersion(mock(HederaSoftwareVersion.class))
                .build();

        final var state = new FakeHederaState();
        state.addService(RecordCacheService.NAME, Map.of("TransactionRecordQueue", new ArrayDeque<String>()));
        app.workingStateAccessor().setHederaState(state);
    }

    @Test
    void objectGraphRootsAreAvailable() {
        given(platform.getContext()).willReturn(platformContext);
        given(platformContext.getMetrics()).willReturn(metrics);

        final IngestInjectionComponent subject =
                app.ingestComponentFactory().get().create();

        assertNotNull(subject.ingestWorkflow());
    }
}
