package com.hedera.services.state.logic;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.state.expiry.EntityAutoRenewal;
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
import com.swirlds.common.system.transaction.SwirldTransaction;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith({ MockitoExtension.class, LogCaptureExtension.class })
class StandardProcessLogicTest {

	private final long member = 1L;
	private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);
	private final Instant triggeredConsensusNow = consensusNow.plusNanos(1L);;

	@Mock
	private ExpiryManager expiries;
	@Mock
	private InvariantChecks invariantChecks;
	@Mock
	private ExpandHandleSpan expandHandleSpan;
	@Mock
	private EntityAutoRenewal autoRenewal;
	@Mock
	private ServicesTxnManager txnManager;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private TxnAccessor triggeredAccessor;
	@Mock
	private SwirldTransaction swirldTransaction;
	@Mock
	private ExecutionTimeTracker executionTimeTracker;
	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private ConsensusTimeTracker consensusTimeTracker;
	@Mock
	private RecordStreaming recordStreaming;
	@Mock
	private ScheduleProcessing scheduleProcessing;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private StandardProcessLogic subject;

	@BeforeEach
	void setUp() {
		subject = new StandardProcessLogic(
				expiries, invariantChecks,
				expandHandleSpan, consensusTimeTracker, autoRenewal, txnManager,
				sigImpactHistorian, txnCtx, scheduleProcessing,
				executionTimeTracker, recordStreaming);
	}

	@Test
	void happyPathFlowsForNonTriggered() throws InvalidProtocolBufferException {
		final InOrder inOrder = inOrder(consensusTimeTracker, scheduleProcessing,
				expiries, executionTimeTracker, txnManager, autoRenewal, sigImpactHistorian, recordStreaming);

		given(expandHandleSpan.accessorFor(swirldTransaction)).willReturn(accessor);
		given(invariantChecks.holdFor(accessor, consensusNow, member)).willReturn(true);
		given(consensusTimeTracker.firstTransactionTime()).willReturn(consensusNow);

		// when:
		subject.incorporateConsensusTxn(swirldTransaction, consensusNow, member);

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
		inOrder.verify(scheduleProcessing).expire(consensusNow);
		inOrder.verify(autoRenewal).execute(consensusNow);
		inOrder.verify(scheduleProcessing, never()).triggerNextTransactionExpiringAsNeeded(any(), any());
	}

	@Test
	void abortsOnFailedInvariantCheck() throws InvalidProtocolBufferException {
		given(expandHandleSpan.accessorFor(swirldTransaction)).willReturn(accessor);

		// when:
		subject.incorporateConsensusTxn(swirldTransaction, consensusNow, member);

		// then:
		verifyNoInteractions(expiries, txnManager, autoRenewal);
	}

	@Test
	void happyPathFlowsForTriggered() throws InvalidProtocolBufferException {
		given(consensusTimeTracker.firstTransactionTime()).willReturn(consensusNow);
		given(consensusTimeTracker.nextTransactionTime(false)).willReturn(triggeredConsensusNow);
		given(expandHandleSpan.accessorFor(swirldTransaction)).willReturn(accessor);
		given(invariantChecks.holdFor(accessor, consensusNow, member)).willReturn(true);
		given(txnCtx.triggeredTxn()).willReturn(triggeredAccessor);

		subject.incorporateConsensusTxn(swirldTransaction, consensusNow, member);

		verify(expiries).purge(consensusNow.getEpochSecond());
		verify(txnManager).process(accessor, consensusNow, member);
		verify(txnManager).process(triggeredAccessor, triggeredConsensusNow, member);
		verify(autoRenewal).execute(consensusNow);
		verify(scheduleProcessing).expire(consensusNow);
		verify(consensusTimeTracker).isFirstUsed();
		verify(consensusTimeTracker).firstTransactionTime();
		verify(consensusTimeTracker).nextTransactionTime(false);
		verify(consensusTimeTracker).reset(consensusNow);
		verify(scheduleProcessing, never()).triggerNextTransactionExpiringAsNeeded(any(), any());
	}

	@Test
	void warnsOnNonGrpc() throws InvalidProtocolBufferException {
		given(expandHandleSpan.accessorFor(swirldTransaction)).willThrow(InvalidProtocolBufferException.class);

		subject.incorporateConsensusTxn(swirldTransaction, consensusNow, member);

		assertThat(logCaptor.warnLogs(), contains(Matchers.startsWith("Consensus platform txn was not gRPC!")));
	}

	@Test
	void logsAtErrorForUnhandledInternalProcessFailure() throws InvalidProtocolBufferException {
		given(expandHandleSpan.accessorFor(swirldTransaction)).willThrow(IllegalStateException.class);

		subject.incorporateConsensusTxn(swirldTransaction, consensusNow, member);

		assertThat(logCaptor.errorLogs(), contains(Matchers.startsWith("Unhandled internal process failure")));
	}

	@Test
	void usesNextTransactionTimeIfFirstUsed() throws InvalidProtocolBufferException {
		given(expandHandleSpan.accessorFor(swirldTransaction)).willReturn(accessor);
		given(invariantChecks.holdFor(accessor, consensusNow, member)).willReturn(true);
		given(consensusTimeTracker.nextTransactionTime(true)).willReturn(consensusNow);
		given(consensusTimeTracker.isFirstUsed()).willReturn(true);

		subject.incorporateConsensusTxn(swirldTransaction, consensusNow, member);

		verify(consensusTimeTracker).reset(consensusNow);
		verify(consensusTimeTracker).isFirstUsed();
		verify(consensusTimeTracker, never()).firstTransactionTime();
		verify(consensusTimeTracker).nextTransactionTime(true);
	}


	@Test
	void happyPathFlowsForScheduled() throws InvalidProtocolBufferException {

		final InOrder inOrder = inOrder(consensusTimeTracker, scheduleProcessing,
				expiries, executionTimeTracker, txnManager, autoRenewal, sigImpactHistorian, recordStreaming);

		given(consensusTimeTracker.firstTransactionTime()).willReturn(consensusNow);
		given(consensusTimeTracker.hasMoreTransactionTime(false)).willReturn(true);
		given(consensusTimeTracker.nextTransactionTime(false)).willReturn(triggeredConsensusNow);
		given(expandHandleSpan.accessorFor(swirldTransaction)).willReturn(accessor);
		given(invariantChecks.holdFor(accessor, consensusNow, member)).willReturn(true);
		given(scheduleProcessing.triggerNextTransactionExpiringAsNeeded(consensusNow, null))
				.willReturn(triggeredAccessor);
		given(scheduleProcessing.triggerNextTransactionExpiringAsNeeded(consensusNow, triggeredAccessor))
				.willReturn(null);
		given(txnCtx.triggeredTxn()).willReturn(null);

		subject.incorporateConsensusTxn(swirldTransaction, consensusNow, member);

		inOrder.verify(consensusTimeTracker).reset(consensusNow);
		inOrder.verify(expiries).purge(consensusNow.getEpochSecond());
		inOrder.verify(consensusTimeTracker).isFirstUsed();
		inOrder.verify(consensusTimeTracker).firstTransactionTime();
		inOrder.verify(txnManager).process(accessor, consensusNow, member);
		inOrder.verify(scheduleProcessing).expire(consensusNow);
		inOrder.verify(consensusTimeTracker).hasMoreTransactionTime(false);
		inOrder.verify(scheduleProcessing).triggerNextTransactionExpiringAsNeeded(consensusNow, null);
		inOrder.verify(consensusTimeTracker, times(1)).nextTransactionTime(false);
		inOrder.verify(txnManager).process(triggeredAccessor, triggeredConsensusNow, member);
		inOrder.verify(consensusTimeTracker).hasMoreTransactionTime(false);
		inOrder.verify(scheduleProcessing).triggerNextTransactionExpiringAsNeeded(consensusNow, triggeredAccessor);
		inOrder.verify(autoRenewal).execute(consensusNow);
	}
}
