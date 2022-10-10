/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services;

import static com.hedera.services.ServicesState.EMPTY_HASH;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_LOG_DIR;
import static com.hedera.services.utils.SleepingPause.SLEEPING_PAUSE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.CurrentPlatformStatus;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.init.ServicesInitFlow;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.context.properties.ChainedSources;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.context.properties.ScreenedNodeFileProps;
import com.hedera.services.grpc.GrpcStarter;
import com.hedera.services.grpc.NettyGrpcServerManager;
import com.hedera.services.ledger.backing.BackingAccounts;
import com.hedera.services.sigs.EventExpansion;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.exports.ServicesSignedStateListener;
import com.hedera.services.state.exports.SignedStateBalancesExporter;
import com.hedera.services.state.exports.ToStringAccountsExporter;
import com.hedera.services.state.forensics.HashLogger;
import com.hedera.services.state.forensics.ServicesIssListener;
import com.hedera.services.state.initialization.BackedSystemAccountsCreator;
import com.hedera.services.state.initialization.HfsSystemFilesManager;
import com.hedera.services.state.initialization.TreasuryCloner;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.state.logic.ReconnectListener;
import com.hedera.services.state.logic.StandardProcessLogic;
import com.hedera.services.state.validation.BasedLedgerValidator;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.stats.ServicesStatsManager;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.txns.network.UpgradeActions;
import com.hedera.services.txns.prefetch.PrefetchProcessor;
import com.hedera.services.utils.JvmSystemExits;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicesAppTest {
    private final long selfId = 123;
    private final String accountMemo = "0.0.3";
    private final NodeId selfNodeId = new NodeId(false, selfId);

    @Mock private Platform platform;
    @Mock private Cryptography cryptography;
    @Mock private PropertySource overridingProps;

    private ServicesApp subject;

    @BeforeEach
    void setUp() {
        // setup:
        final var bootstrapProps = new BootstrapProperties();
        final var props = new ChainedSources(overridingProps, bootstrapProps);
        final var logDirKey = HEDERA_RECORD_STREAM_LOG_DIR;
        final var logDirVal = "data/recordStreams";
        final var nodeProps = new ScreenedNodeFileProps();

        given(platform.getCryptography()).willReturn(cryptography);
        given(platform.getSelfId()).willReturn(selfNodeId);
        if (!nodeProps.containsProperty(logDirKey)) {
            given(overridingProps.containsProperty(any())).willReturn(false);
            given(overridingProps.containsProperty(logDirKey)).willReturn(true);
            given(overridingProps.getProperty(logDirKey)).willReturn(logDirVal);
        }

        subject =
                DaggerServicesApp.builder()
                        .staticAccountMemo(accountMemo)
                        .bootstrapProps(props)
                        .initialHash(EMPTY_HASH)
                        .platform(platform)
                        .crypto(cryptography)
                        .selfId(selfId)
                        .build();
    }

    @Test
    void objectGraphRootsAreAvailable() {
        assertThat(subject.eventExpansion(), instanceOf(EventExpansion.class));
        assertThat(subject.treasuryCloner(), instanceOf(TreasuryCloner.class));
        assertThat(subject.logic(), instanceOf(StandardProcessLogic.class));
        assertThat(subject.hashLogger(), instanceOf(HashLogger.class));
        assertThat(subject.workingState(), instanceOf(MutableStateChildren.class));
        assertThat(subject.dualStateAccessor(), instanceOf(DualStateAccessor.class));
        assertThat(subject.initializationFlow(), instanceOf(ServicesInitFlow.class));
        assertThat(subject.nodeLocalProperties(), instanceOf(NodeLocalProperties.class));
        assertThat(subject.recordStreamManager(), instanceOf(RecordStreamManager.class));
        assertThat(subject.globalDynamicProperties(), instanceOf(GlobalDynamicProperties.class));
        assertThat(subject.grpc(), instanceOf(NettyGrpcServerManager.class));
        assertThat(subject.platformStatus(), instanceOf(CurrentPlatformStatus.class));
        assertThat(subject.accountsExporter(), instanceOf(ToStringAccountsExporter.class));
        assertThat(subject.balancesExporter(), instanceOf(SignedStateBalancesExporter.class));
        assertThat(subject.networkCtxManager(), instanceOf(NetworkCtxManager.class));
        assertThat(subject.sysFilesManager(), instanceOf(HfsSystemFilesManager.class));
        assertThat(subject.backingAccounts(), instanceOf(BackingAccounts.class));
        assertThat(subject.statsManager(), instanceOf(ServicesStatsManager.class));
        assertThat(subject.issListener(), instanceOf(ServicesIssListener.class));
        assertThat(subject.newSignedStateListener(), instanceOf(ServicesSignedStateListener.class));
        assertThat(subject.ledgerValidator(), instanceOf(BasedLedgerValidator.class));
        assertThat(subject.systemExits(), instanceOf(JvmSystemExits.class));
        assertThat(subject.sysAccountsCreator(), instanceOf(BackedSystemAccountsCreator.class));
        assertThat(subject.nodeInfo(), instanceOf(NodeInfo.class));
        assertThat(subject.platform(), instanceOf(Platform.class));
        assertThat(subject.reconnectListener(), instanceOf(ReconnectListener.class));
        assertThat(subject.grpcStarter(), instanceOf(GrpcStarter.class));
        assertThat(subject.upgradeActions(), instanceOf(UpgradeActions.class));
        assertThat(subject.virtualMapFactory(), instanceOf(VirtualMapFactory.class));
        assertThat(subject.prefetchProcessor(), instanceOf(PrefetchProcessor.class));
        assertSame(subject.nodeId(), selfNodeId);
        assertSame(subject.pause(), SLEEPING_PAUSE);
        assertTrue(subject.consoleOut().isEmpty());
    }
}
