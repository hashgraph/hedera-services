/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.info.SelfNodeInfoImpl;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.throttle.ThrottleManager;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.node.app.workflows.handle.SystemFileUpdateFacility;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.config.api.Configuration;
import java.time.InstantSource;
import java.util.Set;
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
                "0123456789012345678901234567890123456789012345678901234567890123",
                "memo",
                new HederaSoftwareVersion(
                        SemanticVersion.newBuilder().major(1).build(),
                        SemanticVersion.newBuilder().major(2).build()));

        final var configProvider = new ConfigProviderImpl(false);
        final var throttleManager = new ThrottleManager();
        final var exchangeRateManager = new ExchangeRateManager();
        app = DaggerHederaInjectionComponent.builder()
                .initTrigger(InitTrigger.GENESIS)
                .platform(platform)
                .crypto(CryptographyHolder.get())
                .bootstrapProps(new BootstrapProperties())
                .configuration(configProvider)
                .systemFileUpdateFacility(
                        new SystemFileUpdateFacility(configProvider, throttleManager, exchangeRateManager))
                .throttleManager(throttleManager)
                .self(selfNodeInfo)
                .initialHash(new Hash())
                .maxSignedTxnSize(1024)
                .currentPlatformStatus(() -> PlatformStatus.ACTIVE)
                .servicesRegistry(Set::of)
                .instantSource(InstantSource.system())
                .exchangeRateManager(exchangeRateManager)
                .build();
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
