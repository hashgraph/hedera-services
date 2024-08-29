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

package com.hedera.node.app.workflows.handle;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.cache.CacheWarmer;
import com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.SystemSetup;
import com.hedera.node.app.workflows.handle.steps.HollowAccountCompletions;
import com.hedera.node.app.workflows.handle.steps.NodeStakeUpdates;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.state.State;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleWorkflowTest {

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private NodeStakeUpdates nodeStakeUpdates;

    @Mock
    private Authorizer authorizer;

    @Mock
    private FeeManager feeManager;

    @Mock
    private DispatchProcessor dispatchProcessor;

    @Mock
    private ServiceScopeLookup serviceScopeLookup;

    @Mock
    private ChildDispatchFactory childDispatchFactory;

    @Mock
    private TransactionDispatcher dispatcher;

    @Mock
    private NetworkUtilizationManager networkUtilizationManager;

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock
    private BlockRecordManager blockRecordManager;

    @Mock
    private CacheWarmer cacheWarmer;

    @Mock
    private HandleWorkflowMetrics handleWorkflowMetrics;

    @Mock
    private ThrottleServiceManager throttleServiceManager;

    @Mock
    private SemanticVersion version;

    @Mock
    private InitTrigger initTrigger;

    @Mock
    private HollowAccountCompletions hollowAccountCompletions;

    @Mock
    private SystemSetup systemSetup;

    @Mock
    private HederaRecordCache recordCache;

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private PreHandleWorkflow preHandleWorkflow;

    @Mock
    private State state;

    @Mock
    private Round round;

    private HandleWorkflow subject;

    @BeforeEach
    void setUp() {
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(DEFAULT_CONFIG, 1L));
        subject = new HandleWorkflow(
                networkInfo,
                nodeStakeUpdates,
                authorizer,
                feeManager,
                dispatchProcessor,
                serviceScopeLookup,
                childDispatchFactory,
                dispatcher,
                networkUtilizationManager,
                configProvider,
                storeMetricsService,
                blockRecordManager,
                cacheWarmer,
                handleWorkflowMetrics,
                throttleServiceManager,
                version,
                initTrigger,
                hollowAccountCompletions,
                systemSetup,
                recordCache,
                exchangeRateManager,
                preHandleWorkflow);
    }

    @Test
    void onlySkipsEventWithMissingCreator() {
        final var presentCreatorId = new NodeId(1L);
        final var missingCreatorId = new NodeId(2L);
        final var eventFromPresentCreator = mock(ConsensusEvent.class);
        final var eventFromMissingCreator = mock(ConsensusEvent.class);
        given(round.iterator())
                .willReturn(List.of(eventFromMissingCreator, eventFromPresentCreator)
                        .iterator());
        given(eventFromPresentCreator.getCreatorId()).willReturn(presentCreatorId);
        given(eventFromMissingCreator.getCreatorId()).willReturn(missingCreatorId);
        given(networkInfo.nodeInfo(presentCreatorId.id())).willReturn(mock(NodeInfo.class));
        given(networkInfo.nodeInfo(missingCreatorId.id())).willReturn(null);
        given(eventFromPresentCreator.consensusTransactionIterator()).willReturn(Collections.emptyIterator());
        given(round.getConsensusTimestamp()).willReturn(Instant.ofEpochSecond(12345L));
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(DEFAULT_CONFIG, 1));

        subject.handleRound(state, round);

        verify(eventFromPresentCreator).consensusTransactionIterator();
        verify(recordCache).resetRoundReceipts();
        verify(recordCache).commitRoundReceipts(any(), any());
    }
}
