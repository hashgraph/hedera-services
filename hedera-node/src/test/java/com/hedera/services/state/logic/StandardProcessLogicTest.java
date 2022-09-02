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
package com.hedera.services.state.logic;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.keys.HederaKeyActivation;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.state.expiry.EntityAutoExpiry;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.stats.ExecutionTimeTracker;
import com.hedera.services.txns.schedule.ScheduleProcessing;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class StandardProcessLogicTest {

    private final long member = 1L;
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);
    private final Instant triggeredConsensusNow = consensusNow.plusNanos(1L);

    @Mock private ExpiryManager expiries;
    @Mock private InvariantChecks invariantChecks;
    @Mock private ExpandHandleSpan expandHandleSpan;
    @Mock private EntityAutoExpiry autoRenewal;
    @Mock private ServicesTxnManager txnManager;
    @Mock private TransactionContext txnCtx;
    @Mock private PlatformTxnAccessor accessor;
    @Mock private TxnAccessor triggeredAccessor;
    @Mock private ExecutionTimeTracker executionTimeTracker;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private ConsensusTimeTracker consensusTimeTracker;
    @Mock private RecordStreaming recordStreaming;
    @Mock private ScheduleProcessing scheduleProcessing;
    @Mock private StateView workingView;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private StandardProcessLogic subject;

    private Transaction txn = new SwirldTransaction();

    @BeforeEach
    void setUp() {
        subject =
                new StandardProcessLogic(
                        expiries,
                        invariantChecks,
                        expandHandleSpan,
                        consensusTimeTracker,
                        autoRenewal,
                        txnManager,
                        sigImpactHistorian,
                        txnCtx,
                        scheduleProcessing,
                        executionTimeTracker,
                        recordStreaming,
                        workingView);
    }

    @Test
    void happyPathFlowsForNonTriggered() throws InvalidProtocolBufferException {
        final InOrder inOrder =
                inOrder(
                        consensusTimeTracker,
                        scheduleProcessing,
                        expiries,
                        executionTimeTracker,
                        txnManager,
                        autoRenewal,
                        sigImpactHistorian,
                        recordStreaming);

        given(expandHandleSpan.accessorFor(txn)).willReturn(accessor);
        given(invariantChecks.holdFor(accessor, consensusNow, member)).willReturn(true);
        given(consensusTimeTracker.firstTransactionTime()).willReturn(consensusNow);
        given(scheduleProcessing.shouldProcessScheduledTransactions(consensusNow)).willReturn(true);
        given(scheduleProcessing.getMaxProcessingLoopIterations()).willReturn(10L);

        txn.add(HederaKeyActivation.VALID_IMPLICIT_SIG);

        // when:
        subject.incorporateConsensusTxn(txn, consensusNow, member);

        // then:
        inOrder.verify(consensusTimeTracker).reset(consensusNow);
        inOrder.verify(sigImpactHistorian).setChangeTime(consensusNow);
        inOrder.verify(expiries).purge(consensusNow.getEpochSecond());
        inOrder.verify(sigImpactHistorian).purge();
        inOrder.verify(recordStreaming).resetBlockNo();
        inOrder.verify(consensusTimeTracker).isFirstUsed();
        inOrder.verify(consensusTimeTracker).firstTransactionTime();
        inOrder.verify(executionTimeTracker).start();
        inOrder.verify(txnManager).process(accessor, consensusNow, member);
        inOrder.verify(executionTimeTracker).stop();
        inOrder.verify(scheduleProcessing)
                .triggerNextTransactionExpiringAsNeeded(consensusNow, null, true);
        inOrder.verify(autoRenewal).execute(consensusNow);
        assertTrue(txn.getSignatures().isEmpty());
    }

    @Test
    void happyPathFlowsNoScheduleProcessing() throws InvalidProtocolBufferException {
        final InOrder inOrder =
                inOrder(
                        consensusTimeTracker,
                        scheduleProcessing,
                        expiries,
                        executionTimeTracker,
                        txnManager,
                        autoRenewal,
                        sigImpactHistorian,
                        recordStreaming);

        given(expandHandleSpan.accessorFor(txn)).willReturn(accessor);
        given(invariantChecks.holdFor(accessor, consensusNow, member)).willReturn(true);
        given(consensusTimeTracker.firstTransactionTime()).willReturn(consensusNow);

        // when:
        subject.incorporateConsensusTxn(txn, consensusNow, member);

        // then:
        inOrder.verify(consensusTimeTracker).reset(consensusNow);
        inOrder.verify(sigImpactHistorian).setChangeTime(consensusNow);
        inOrder.verify(expiries).purge(consensusNow.getEpochSecond());
        inOrder.verify(sigImpactHistorian).purge();
        inOrder.verify(recordStreaming).resetBlockNo();
        inOrder.verify(consensusTimeTracker).isFirstUsed();
        inOrder.verify(consensusTimeTracker).firstTransactionTime();
        inOrder.verify(executionTimeTracker).start();
        inOrder.verify(txnManager).process(accessor, consensusNow, member);
        inOrder.verify(executionTimeTracker).stop();
        inOrder.verify(autoRenewal).execute(consensusNow);

        verify(scheduleProcessing, never())
                .triggerNextTransactionExpiringAsNeeded(any(), any(), anyBoolean());
        verify(scheduleProcessing, never()).getMaxProcessingLoopIterations();
    }

    @Test
    void abortsOnFailedInvariantCheck() throws InvalidProtocolBufferException {
        given(expandHandleSpan.accessorFor(txn)).willReturn(accessor);

        // when:
        subject.incorporateConsensusTxn(txn, consensusNow, member);

        // then:
        verifyNoInteractions(expiries, txnManager, autoRenewal);
    }

    @Test
    void happyPathFlowsForTriggered() throws InvalidProtocolBufferException {
        given(consensusTimeTracker.firstTransactionTime()).willReturn(consensusNow);
        given(consensusTimeTracker.nextTransactionTime(false)).willReturn(triggeredConsensusNow);
        given(expandHandleSpan.accessorFor(txn)).willReturn(accessor);
        given(invariantChecks.holdFor(accessor, consensusNow, member)).willReturn(true);
        given(txnCtx.triggeredTxn()).willReturn(triggeredAccessor);
        given(scheduleProcessing.shouldProcessScheduledTransactions(consensusNow)).willReturn(true);
        given(scheduleProcessing.getMaxProcessingLoopIterations()).willReturn(10L);

        subject.incorporateConsensusTxn(txn, consensusNow, member);

        verify(expiries).purge(consensusNow.getEpochSecond());
        verify(txnManager).process(accessor, consensusNow, member);
        verify(txnManager).process(triggeredAccessor, triggeredConsensusNow, member);
        verify(autoRenewal).execute(consensusNow);
        verify(consensusTimeTracker).isFirstUsed();
        verify(consensusTimeTracker).firstTransactionTime();
        verify(consensusTimeTracker).nextTransactionTime(false);
        verify(consensusTimeTracker).reset(consensusNow);
        verify(scheduleProcessing).triggerNextTransactionExpiringAsNeeded(consensusNow, null, true);
    }

    @Test
    void warnsOnNonGrpc() throws InvalidProtocolBufferException {
        given(expandHandleSpan.accessorFor(txn)).willThrow(InvalidProtocolBufferException.class);

        subject.incorporateConsensusTxn(txn, consensusNow, member);

        assertThat(
                logCaptor.warnLogs(),
                contains(Matchers.startsWith("Consensus platform txn was not gRPC!")));
    }

    @Test
    void logsAtErrorForUnhandledInternalProcessFailure() throws InvalidProtocolBufferException {
        given(expandHandleSpan.accessorFor(txn)).willThrow(IllegalStateException.class);

        subject.incorporateConsensusTxn(txn, consensusNow, member);

        assertThat(
                logCaptor.errorLogs(),
                contains(Matchers.startsWith("Unhandled internal process failure")));
    }

    @Test
    void usesNextTransactionTimeIfFirstUsed() throws InvalidProtocolBufferException {
        given(expandHandleSpan.accessorFor(txn)).willReturn(accessor);
        given(invariantChecks.holdFor(accessor, consensusNow, member)).willReturn(true);
        given(consensusTimeTracker.nextTransactionTime(true)).willReturn(consensusNow);
        given(consensusTimeTracker.isFirstUsed()).willReturn(true);
        given(scheduleProcessing.shouldProcessScheduledTransactions(consensusNow)).willReturn(true);
        given(scheduleProcessing.getMaxProcessingLoopIterations()).willReturn(10L);

        subject.incorporateConsensusTxn(txn, consensusNow, member);

        verify(consensusTimeTracker).reset(consensusNow);
        verify(consensusTimeTracker).isFirstUsed();
        verify(consensusTimeTracker, never()).firstTransactionTime();
        verify(consensusTimeTracker).nextTransactionTime(true);
    }

    @Test
    void happyPathFlowsForScheduled() throws InvalidProtocolBufferException {

        final InOrder inOrder =
                inOrder(
                        consensusTimeTracker,
                        scheduleProcessing,
                        expiries,
                        executionTimeTracker,
                        txnManager,
                        autoRenewal,
                        sigImpactHistorian,
                        recordStreaming);

        given(consensusTimeTracker.firstTransactionTime()).willReturn(consensusNow);
        given(consensusTimeTracker.hasMoreTransactionTime(false)).willReturn(true, false);
        given(consensusTimeTracker.nextTransactionTime(false)).willReturn(triggeredConsensusNow);
        given(expandHandleSpan.accessorFor(txn)).willReturn(accessor);
        given(invariantChecks.holdFor(accessor, consensusNow, member)).willReturn(true);
        given(scheduleProcessing.triggerNextTransactionExpiringAsNeeded(consensusNow, null, false))
                .willReturn(triggeredAccessor);
        given(
                        scheduleProcessing.triggerNextTransactionExpiringAsNeeded(
                                consensusNow, triggeredAccessor, true))
                .willReturn(null);
        given(txnCtx.triggeredTxn()).willReturn(null);
        given(scheduleProcessing.shouldProcessScheduledTransactions(consensusNow)).willReturn(true);
        given(scheduleProcessing.getMaxProcessingLoopIterations()).willReturn(10L);

        subject.incorporateConsensusTxn(txn, consensusNow, member);

        inOrder.verify(consensusTimeTracker).reset(consensusNow);
        inOrder.verify(expiries).purge(consensusNow.getEpochSecond());
        inOrder.verify(consensusTimeTracker).isFirstUsed();
        inOrder.verify(consensusTimeTracker).firstTransactionTime();
        inOrder.verify(txnManager).process(accessor, consensusNow, member);
        inOrder.verify(consensusTimeTracker).hasMoreTransactionTime(false);
        inOrder.verify(scheduleProcessing)
                .triggerNextTransactionExpiringAsNeeded(consensusNow, null, false);
        inOrder.verify(consensusTimeTracker, times(1)).nextTransactionTime(false);
        inOrder.verify(txnManager).process(triggeredAccessor, triggeredConsensusNow, member);
        inOrder.verify(consensusTimeTracker).hasMoreTransactionTime(false);
        inOrder.verify(scheduleProcessing)
                .triggerNextTransactionExpiringAsNeeded(consensusNow, triggeredAccessor, true);
        inOrder.verify(autoRenewal).execute(consensusNow);
    }

    @Test
    void scheduleProcessingLimitedToMaxLoopIterations() throws InvalidProtocolBufferException {

        final InOrder inOrder =
                inOrder(
                        consensusTimeTracker,
                        scheduleProcessing,
                        expiries,
                        executionTimeTracker,
                        txnManager,
                        autoRenewal,
                        sigImpactHistorian,
                        recordStreaming);

        given(consensusTimeTracker.firstTransactionTime()).willReturn(consensusNow);
        given(consensusTimeTracker.hasMoreTransactionTime(false)).willReturn(true);
        given(consensusTimeTracker.nextTransactionTime(false)).willReturn(triggeredConsensusNow);
        given(expandHandleSpan.accessorFor(txn)).willReturn(accessor);
        given(invariantChecks.holdFor(accessor, consensusNow, member)).willReturn(true);
        given(scheduleProcessing.triggerNextTransactionExpiringAsNeeded(consensusNow, null, false))
                .willReturn(triggeredAccessor);
        given(
                        scheduleProcessing.triggerNextTransactionExpiringAsNeeded(
                                consensusNow, triggeredAccessor, false))
                .willReturn(triggeredAccessor);
        given(txnCtx.triggeredTxn()).willReturn(null);
        given(scheduleProcessing.shouldProcessScheduledTransactions(consensusNow)).willReturn(true);
        given(scheduleProcessing.getMaxProcessingLoopIterations()).willReturn(4L);

        subject.incorporateConsensusTxn(txn, consensusNow, member);

        inOrder.verify(consensusTimeTracker).reset(consensusNow);
        inOrder.verify(expiries).purge(consensusNow.getEpochSecond());
        inOrder.verify(consensusTimeTracker).isFirstUsed();
        inOrder.verify(consensusTimeTracker).firstTransactionTime();
        inOrder.verify(txnManager).process(accessor, consensusNow, member);
        inOrder.verify(consensusTimeTracker, times(1)).hasMoreTransactionTime(false);
        inOrder.verify(scheduleProcessing, times(1))
                .triggerNextTransactionExpiringAsNeeded(consensusNow, null, false);
        inOrder.verify(consensusTimeTracker, times(1)).nextTransactionTime(false);
        inOrder.verify(txnManager, times(1))
                .process(triggeredAccessor, triggeredConsensusNow, member);

        inOrder.verify(consensusTimeTracker, times(1)).hasMoreTransactionTime(false);
        inOrder.verify(scheduleProcessing, times(1))
                .triggerNextTransactionExpiringAsNeeded(consensusNow, triggeredAccessor, false);
        inOrder.verify(consensusTimeTracker, times(1)).nextTransactionTime(false);
        inOrder.verify(txnManager, times(1))
                .process(triggeredAccessor, triggeredConsensusNow, member);

        inOrder.verify(consensusTimeTracker, times(1)).hasMoreTransactionTime(false);
        inOrder.verify(scheduleProcessing, times(1))
                .triggerNextTransactionExpiringAsNeeded(consensusNow, triggeredAccessor, false);
        inOrder.verify(consensusTimeTracker, times(1)).nextTransactionTime(false);
        inOrder.verify(txnManager, times(1))
                .process(triggeredAccessor, triggeredConsensusNow, member);

        inOrder.verify(consensusTimeTracker, times(1)).hasMoreTransactionTime(false);
        inOrder.verify(scheduleProcessing, times(1))
                .triggerNextTransactionExpiringAsNeeded(consensusNow, triggeredAccessor, false);
        inOrder.verify(consensusTimeTracker, times(1)).nextTransactionTime(false);
        inOrder.verify(txnManager, times(1))
                .process(triggeredAccessor, triggeredConsensusNow, member);

        inOrder.verify(autoRenewal).execute(consensusNow);

        inOrder.verifyNoMoreInteractions();
    }
}
