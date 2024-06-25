/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fixtures.AppTestBase;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.TransactionScenarioBuilder;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.PreHandleResultManager;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserRecordInitializer;
import com.hedera.node.app.workflows.handle.flow.txn.DefaultHandleWorkflow;
import com.hedera.node.app.workflows.handle.flow.txn.UserTxnWorkflow;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.GenesisWorkflow;
import com.hedera.node.app.workflows.prehandle.FakeSignatureVerificationFuture;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleResult.Status;
import com.hedera.node.config.ConfigProvider;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleWorkflowTest extends AppTestBase {

    private static final Instant CONSENSUS_NOW = Instant.parse("2000-01-01T00:00:00Z");
    private static final long CONFIG_VERSION = 11L;

    private static final PreHandleResult OK_RESULT = createPreHandleResult(Status.SO_FAR_SO_GOOD, ResponseCodeEnum.OK);

    private static final ExchangeRateSet EXCHANGE_RATE_SET =
            ExchangeRateSet.newBuilder().build();

    private static final Fees DEFAULT_FEES = new Fees(1L, 20L, 300L);

    private static PreHandleResult createPreHandleResult(@NonNull Status status, @NonNull ResponseCodeEnum code) {
        final var key = ALICE.account().keyOrThrow();
        return new PreHandleResult(
                ALICE.accountID(),
                key,
                status,
                code,
                new TransactionScenarioBuilder().txInfo(),
                Set.of(),
                Set.of(),
                Set.of(),
                Map.of(key, FakeSignatureVerificationFuture.goodFuture(key)),
                null,
                CONFIG_VERSION);
    }

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private ThrottleServiceManager throttleServiceManager;

    @Mock
    private TransactionDispatcher dispatcher;

    @Mock
    private BlockRecordManager blockRecordManager;

    @Mock
    private Round round;

    @Mock
    private ConsensusEvent event;

    @Mock
    private SwirldTransaction platformTxn;

    @Mock
    private PlatformState platformState;

    @Mock
    private CacheWarmer cacheWarmer;

    @Mock
    private HandleWorkflowMetrics handleWorkflowMetrics;

    @Mock
    private HederaState state;

    @Mock
    private NodeInfo nodeInfo;

    @Mock
    private ConsensusTransactionImpl txn;

    @Mock
    private UserTxnWorkflow userTxnWorkflow;

    @Mock
    private PreHandleResultManager preHandleResultManager;

    @Mock
    private UserRecordInitializer userRecordInitializer;

    @Mock
    private SoftwareVersion version;

    @Mock
    private InitTrigger initTrigger;

    @Mock
    private DefaultHandleWorkflow defaultHandleWorkflow;

    @Mock
    private GenesisWorkflow genesisWorkflow;

    @Mock
    private HederaRecordCache recordCache;

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private StoreMetricsService storeMetricsService;

    @InjectMocks
    private HandleWorkflow workflow;

    @BeforeEach
    void setup() {
        setupStandardStates();

        accountsState.put(
                ALICE.accountID(),
                ALICE.account()
                        .copyBuilder()
                        .tinybarBalance(DEFAULT_FEES.totalFee())
                        .build());
        accountsState.put(
                nodeSelfAccountId,
                nodeSelfAccount
                        .copyBuilder()
                        .tinybarBalance(DEFAULT_FEES.totalFee())
                        .build());
        accountsState.commit();

        workflow = new HandleWorkflow(
                networkInfo,
                configProvider,
                storeMetricsService,
                blockRecordManager,
                cacheWarmer,
                handleWorkflowMetrics,
                throttleServiceManager,
                userRecordInitializer,
                preHandleResultManager,
                version,
                initTrigger,
                defaultHandleWorkflow,
                genesisWorkflow,
                recordCache,
                exchangeRateManager);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    @DisplayName("System transaction is skipped")
    void testPlatformTxnIsSkipped() {
        // given
        when(platformTxn.isSystem()).thenReturn(true);
        when(round.iterator()).thenReturn(List.of(event).iterator());
        when(event.consensusTransactionIterator())
                .thenReturn(List.<ConsensusTransaction>of(platformTxn).iterator());
        when(event.getCreatorId()).thenReturn(nodeSelfId);
        when(round.iterator()).thenReturn(List.of(event).iterator());
        when(event.consensusTransactionIterator())
                .thenReturn(List.<ConsensusTransaction>of(platformTxn).iterator());
        when(event.getCreatorId()).thenReturn(nodeSelfId);
        lenient().when(blockRecordManager.consTimeOfLastHandledTxn()).thenReturn(CONSENSUS_NOW.minusSeconds(1));

        when(networkInfo.nodeInfo(nodeSelfId.id())).thenReturn(selfNodeInfo);

        workflow.handleRound(state, platformState, round);

        // then
        assertThat(accountsState.isModified()).isFalse();
        assertThat(aliasesState.isModified()).isFalse();
        verify(blockRecordManager, never()).advanceConsensusClock(any(), any());
        verify(blockRecordManager, never()).startUserTransaction(any(), any(), any());
        verify(blockRecordManager, never()).endUserTransaction(any(), any());
        verify(throttleServiceManager).updateAllMetrics();
    }

    @Nested
    @DisplayName("Tests for cases when preHandle ran successfully")
    final class AddMissingSignaturesTest {
        @Test
        @DisplayName("Add failing verification result, if a key was handled in preHandle")
        void testRequiredExistingKeyWithFailingSignature() {
            when(platformTxn.isSystem()).thenReturn(false);
            when(round.iterator()).thenReturn(List.of(event).iterator());
            when(event.consensusTransactionIterator())
                    .thenReturn(List.<ConsensusTransaction>of(platformTxn).iterator());
            when(event.getCreatorId()).thenReturn(nodeSelfId);
            when(round.iterator()).thenReturn(List.of(event).iterator());
            when(event.consensusTransactionIterator())
                    .thenReturn(List.<ConsensusTransaction>of(platformTxn).iterator());
            when(event.getCreatorId()).thenReturn(nodeSelfId);
            when(networkInfo.nodeInfo(nodeSelfId.id())).thenReturn(selfNodeInfo);
            lenient().when(blockRecordManager.consTimeOfLastHandledTxn()).thenReturn(CONSENSUS_NOW.minusSeconds(1));

            workflow.handleRound(state, platformState, round);

            verify(dispatcher, never()).dispatchHandle(any());
        }

        @Test
        @DisplayName("Trigger failing verification, if new key was found")
        void testRequiredNewKeyWithFailingSignature() {
            when(platformTxn.isSystem()).thenReturn(false);
            when(round.iterator()).thenReturn(List.of(event).iterator());
            when(event.consensusTransactionIterator())
                    .thenReturn(List.<ConsensusTransaction>of(platformTxn).iterator());
            when(event.getCreatorId()).thenReturn(nodeSelfId);
            when(round.iterator()).thenReturn(List.of(event).iterator());
            when(event.consensusTransactionIterator())
                    .thenReturn(List.<ConsensusTransaction>of(platformTxn).iterator());
            when(event.getCreatorId()).thenReturn(nodeSelfId);
            when(networkInfo.nodeInfo(nodeSelfId.id())).thenReturn(selfNodeInfo);
            lenient().when(blockRecordManager.consTimeOfLastHandledTxn()).thenReturn(CONSENSUS_NOW.minusSeconds(1));

            workflow.handleRound(state, platformState, round);

            verify(dispatcher, never()).dispatchHandle(any());
        }
    }

    @Test
    void handleRoundNoUserTransactions() {
        when(round.iterator()).thenReturn(mock(Iterator.class));

        workflow.handleRound(state, platformState, round);

        verify(cacheWarmer).warm(state, round);
        verify(throttleServiceManager).updateAllMetrics();
        verify(blockRecordManager, never()).endRound(any());
    }

    @Test
    void handleRoundWithUserTransactions() {
        Iterator<ConsensusEvent> eventIterator = mock(Iterator.class);
        when(round.iterator()).thenReturn(eventIterator);
        when(eventIterator.hasNext()).thenReturn(true, false);
        when(eventIterator.next()).thenReturn(event);
        when(networkInfo.nodeInfo(anyLong())).thenReturn(nodeInfo);

        Iterator<ConsensusTransaction> txnIterator = mock(Iterator.class);
        when(event.consensusTransactionIterator()).thenReturn(txnIterator);
        when(txnIterator.hasNext()).thenReturn(true, false);
        when(txnIterator.next()).thenReturn(txn);
        when(txn.isSystem()).thenReturn(false);
        // TODO
        when(userTxnWorkflow.execute()).thenReturn(mock(Stream.class));
        when(event.getCreatorId()).thenReturn(nodeSelfId);
        when(txn.getConsensusTimestamp()).thenReturn(CONSENSUS_NOW);

        workflow.handleRound(state, platformState, round);

        verify(blockRecordManager).startUserTransaction(any(), eq(state), eq(platformState));
        verify(throttleServiceManager).updateAllMetrics();
        verify(blockRecordManager).endRound(state);
    }

    @Test
    void handleRoundEventCreatorNotInAddressBook() {
        when(round.iterator()).thenReturn(mock(Iterator.class));

        workflow.handleRound(state, platformState, round);
        verifyNoInteractions(blockRecordManager);
    }

    @Test
    void handlePlatformTransaction() {
        // TODO
        when(userTxnWorkflow.execute()).thenReturn(mock(Stream.class));
        when(txn.getConsensusTimestamp()).thenReturn(CONSENSUS_NOW);

        workflow.handlePlatformTransaction(state, platformState, event, nodeInfo, txn);

        verify(blockRecordManager).startUserTransaction(any(), eq(state), eq(platformState));
        verify(blockRecordManager).endUserTransaction(any(), eq(state));
        verify(handleWorkflowMetrics).updateTransactionDuration(any(), anyInt());
    }
}
