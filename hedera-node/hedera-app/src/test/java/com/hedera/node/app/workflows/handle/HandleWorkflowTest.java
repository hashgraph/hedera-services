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

import static com.hedera.node.config.types.StreamMode.BOTH;
import static com.hedera.node.config.types.StreamMode.RECORDS;
import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.impl.KVStateChangeListener;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.addressbook.impl.helpers.AddressBookHelper;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeInfoHelper;
import com.hedera.node.app.service.token.impl.handlers.staking.StakePeriodManager;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.workflows.OpWorkflowMetrics;
import com.hedera.node.app.workflows.handle.cache.CacheWarmer;
import com.hedera.node.app.workflows.handle.record.SystemSetup;
import com.hedera.node.app.workflows.handle.steps.HollowAccountCompletions;
import com.hedera.node.app.workflows.handle.steps.StakePeriodChanges;
import com.hedera.node.app.workflows.handle.steps.UserTxnFactory;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.node.config.types.StreamMode;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleWorkflowTest {
    private static final Timestamp BLOCK_TIME = new Timestamp(1_234_567L, 890);

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private StakePeriodChanges stakePeriodChanges;

    @Mock
    private DispatchProcessor dispatchProcessor;

    @Mock
    private StakePeriodManager stakePeriodManager;

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock
    private BlockRecordManager blockRecordManager;

    @Mock
    private BlockStreamManager blockStreamManager;

    @Mock
    private CacheWarmer cacheWarmer;

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private KVStateChangeListener kvStateChangeListener;

    @Mock
    private BoundaryStateChangeListener boundaryStateChangeListener;

    @Mock
    private OpWorkflowMetrics opWorkflowMetrics;

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
    private State state;

    @Mock
    private Round round;

    @Mock
    private StakeInfoHelper stakeInfoHelper;

    @Mock
    private UserTxnFactory userTxnFactory;

    @Mock
    private TssBaseService tssBaseService;

    private HandleWorkflow subject;

    @BeforeEach
    void setUp() {}

    @Test
    void onlySkipsEventWithMissingCreator() {
        final var presentCreatorId = NodeId.of(1L);
        final var missingCreatorId = NodeId.of(2L);
        final var eventFromPresentCreator = mock(ConsensusEvent.class);
        final var eventFromMissingCreator = mock(ConsensusEvent.class);
        given(round.iterator())
                .willReturn(List.of(eventFromMissingCreator, eventFromPresentCreator)
                        .iterator());
        given(eventFromPresentCreator.getCreatorId()).willReturn(presentCreatorId);
        given(eventFromMissingCreator.getCreatorId()).willReturn(missingCreatorId);
        given(networkInfo.nodeInfo(presentCreatorId.id())).willReturn(mock(NodeInfo.class));
        given(networkInfo.nodeInfo(missingCreatorId.id())).willReturn(null);
        given(eventFromPresentCreator.consensusTransactionIterator()).willReturn(emptyIterator());
        given(round.getConsensusTimestamp()).willReturn(Instant.ofEpochSecond(12345L));

        givenSubjectWith(RECORDS, emptyList());

        subject.handleRound(state, round);

        verify(eventFromPresentCreator).consensusTransactionIterator();
        verify(recordCache).resetRoundReceipts();
        verify(recordCache).commitRoundReceipts(any(), any());
    }

    @Test
    void writesEachMigrationStateChangeWithBlockTimestamp() {
        given(round.iterator()).willReturn(emptyIterator());
        final var firstBuilder = StateChanges.newBuilder().stateChanges(List.of(StateChange.DEFAULT));
        final var secondBuilder =
                StateChanges.newBuilder().stateChanges(List.of(StateChange.DEFAULT, StateChange.DEFAULT));
        final var builders = List.of(firstBuilder, secondBuilder);
        givenSubjectWith(BOTH, builders);
        given(blockStreamManager.blockTimestamp()).willReturn(BLOCK_TIME);

        subject.handleRound(state, round);

        builders.forEach(builder -> verify(blockStreamManager)
                .writeItem(BlockItem.newBuilder()
                        .stateChanges(builder.consensusTimestamp(BLOCK_TIME).build())
                        .build()));
    }

    private void givenSubjectWith(
            @NonNull final StreamMode mode, @NonNull final List<StateChanges.Builder> migrationStateChanges) {
        final var config = HederaTestConfigBuilder.create()
                .withValue("blockStream.streamMode", "" + mode)
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(config, 1L));
        subject = new HandleWorkflow(
                networkInfo,
                stakePeriodChanges,
                dispatchProcessor,
                configProvider,
                storeMetricsService,
                blockRecordManager,
                blockStreamManager,
                cacheWarmer,
                opWorkflowMetrics,
                throttleServiceManager,
                version,
                initTrigger,
                hollowAccountCompletions,
                systemSetup,
                stakeInfoHelper,
                recordCache,
                exchangeRateManager,
                stakePeriodManager,
                migrationStateChanges,
                userTxnFactory,
                new AddressBookHelper(),
                tssBaseService,
                kvStateChangeListener,
                boundaryStateChangeListener,
                scheduleService);
    }
}
