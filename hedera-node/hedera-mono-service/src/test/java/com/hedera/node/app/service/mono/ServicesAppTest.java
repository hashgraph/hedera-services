/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono;

import static com.hedera.node.app.service.mono.ServicesState.EMPTY_HASH;
import static com.hedera.node.app.service.mono.utils.SleepingPause.SLEEPING_PAUSE;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_LOG_DIR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.context.init.ServicesInitFlow;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.ChainedSources;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.context.properties.ScreenedNodeFileProps;
import com.hedera.node.app.service.mono.grpc.GrpcStarter;
import com.hedera.node.app.service.mono.grpc.NettyGrpcServerManager;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakeStartupHelper;
import com.hedera.node.app.service.mono.ledger.backing.BackingAccounts;
import com.hedera.node.app.service.mono.sigs.EventExpansion;
import com.hedera.node.app.service.mono.state.DualStateAccessor;
import com.hedera.node.app.service.mono.state.exports.ServicesSignedStateListener;
import com.hedera.node.app.service.mono.state.exports.SignedStateBalancesExporter;
import com.hedera.node.app.service.mono.state.exports.ToStringAccountsExporter;
import com.hedera.node.app.service.mono.state.forensics.HashLogger;
import com.hedera.node.app.service.mono.state.forensics.ServicesIssListener;
import com.hedera.node.app.service.mono.state.initialization.BackedSystemAccountsCreator;
import com.hedera.node.app.service.mono.state.initialization.HfsSystemFilesManager;
import com.hedera.node.app.service.mono.state.initialization.TreasuryCloner;
import com.hedera.node.app.service.mono.state.logic.NetworkCtxManager;
import com.hedera.node.app.service.mono.state.logic.ReconnectListener;
import com.hedera.node.app.service.mono.state.logic.StandardProcessLogic;
import com.hedera.node.app.service.mono.state.validation.BasedLedgerValidator;
import com.hedera.node.app.service.mono.state.virtual.VirtualMapFactory;
import com.hedera.node.app.service.mono.stats.ServicesStatsManager;
import com.hedera.node.app.service.mono.stream.RecordStreamManager;
import com.hedera.node.app.service.mono.txns.network.UpgradeActions;
import com.hedera.node.app.service.mono.txns.prefetch.PrefetchProcessor;
import com.hedera.node.app.service.mono.utils.JvmSystemExits;
import com.swirlds.common.context.PlatformContext;
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

    @Mock
    private Platform platform;

    @Mock
    private Cryptography cryptography;

    @Mock
    private PlatformContext platformContext;

    @Mock
    private PropertySource overridingProps;

    private ServicesApp subject;

    @BeforeEach
    void setUp() {
        // setup:
        final var bootstrapProps = new BootstrapProperties();
        final var props = new ChainedSources(overridingProps, bootstrapProps);
        final var logDirKey = HEDERA_RECORD_STREAM_LOG_DIR;
        final var logDirVal = "data/recordStreams";
        final var nodeProps = new ScreenedNodeFileProps();

        given(platform.getContext()).willReturn(platformContext);
        given(platformContext.getCryptography()).willReturn(cryptography);
        given(platform.getSelfId()).willReturn(selfNodeId);
        if (!nodeProps.containsProperty(logDirKey)) {
            given(overridingProps.containsProperty(any())).willReturn(false);
            given(overridingProps.containsProperty(logDirKey)).willReturn(true);
            given(overridingProps.getProperty(logDirKey)).willReturn(logDirVal);
        }

        subject = DaggerServicesApp.builder()
                .staticAccountMemo(accountMemo)
                .bootstrapProps(props)
                .initialHash(EMPTY_HASH)
                .platform(platform)
                .consoleCreator((ignore, visible) -> null)
                .crypto(cryptography)
                .selfId(selfId)
                .build();
    }

    @Test
    @SuppressWarnings("java:S5961")
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
        assertThat(subject.bootstrapProps(), instanceOf(ChainedSources.class));
        assertSame(subject.nodeId(), selfNodeId);
        assertSame(SLEEPING_PAUSE, subject.pause());
        assertTrue(subject.consoleOut().isEmpty());
        assertThat(subject.stakeStartupHelper(), instanceOf(StakeStartupHelper.class));
    }
}
