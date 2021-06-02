package com.hedera.services.legacy.services.state;

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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.charging.FeeChargingPolicy;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.security.ops.SystemOpAuthorization;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.state.expiry.EntityAutoRenewal;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.logic.InvariantChecks;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.txns.ExpandHandleSpan;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.RunningHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.when;
import static org.mockito.Mockito.never;

class AwareProcessLogicTest {
	private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L);

	private SwirldTransaction platformTxn;
	private InvariantChecks invariantChecks;
	private ServicesContext ctx;
	private ExpiryManager expiryManager;
	private TransactionContext txnCtx;
	private ExpandHandleSpan expandHandleSpan;

	private AwareProcessLogic subject;

	@BeforeEach
	void setup() {
		final SwirldTransaction txn = mock(SwirldTransaction.class);
		final PlatformTxnAccessor txnAccessor = mock(PlatformTxnAccessor.class);
		final HederaLedger ledger = mock(HederaLedger.class);
		final AccountRecordsHistorian historian = mock(AccountRecordsHistorian.class);
		final HederaSigningOrder keyOrder = mock(HederaSigningOrder.class);
		final SigningOrderResult orderResult = mock(SigningOrderResult.class);
		final MiscRunningAvgs runningAvgs = mock(MiscRunningAvgs.class);
		final MiscSpeedometers speedometers = mock(MiscSpeedometers.class);
		final FeeCalculator fees = mock(FeeCalculator.class);
		final TxnIdRecentHistory recentHistory = mock(TxnIdRecentHistory.class);
		final Map<TransactionID, TxnIdRecentHistory> histories = mock(Map.class);
		final AccountID accountID = mock(AccountID.class);
		final FeeChargingPolicy policy = mock(FeeChargingPolicy.class);
		final SystemOpPolicies policies = mock(SystemOpPolicies.class);
		final TransitionLogicLookup lookup = mock(TransitionLogicLookup.class);
		final EntityAutoRenewal entityAutoRenewal = mock(EntityAutoRenewal.class);

		invariantChecks = mock(InvariantChecks.class);
		expiryManager = mock(ExpiryManager.class);

		txnCtx = mock(TransactionContext.class);

		TransactionBody txnBody = mock(TransactionBody.class);

		ctx = mock(ServicesContext.class);
		given(ctx.ledger()).willReturn(ledger);
		given(ctx.txnCtx()).willReturn(txnCtx);
		given(ctx.recordsHistorian()).willReturn(historian);
		given(ctx.backedKeyOrder()).willReturn(keyOrder);
		given(ctx.runningAvgs()).willReturn(runningAvgs);
		given(ctx.speedometers()).willReturn(speedometers);
		given(ctx.fees()).willReturn(fees);
		given(ctx.txnHistories()).willReturn(histories);
		given(ctx.txnChargingPolicy()).willReturn(policy);
		given(ctx.systemOpPolicies()).willReturn(policies);
		given(ctx.transitionLogic()).willReturn(lookup);
		given(ctx.invariants()).willReturn(invariantChecks);
		given(ctx.expiries()).willReturn(expiryManager);

		given(txnCtx.accessor()).willReturn(txnAccessor);
		given(txnCtx.submittingNodeAccount()).willReturn(accountID);
		given(txnCtx.isPayerSigKnownActive()).willReturn(true);
		given(txnAccessor.getPlatformTxn()).willReturn(txn);

		given(txn.getSignatures()).willReturn(Collections.emptyList());
		given(keyOrder.keysForPayer(any(), any())).willReturn(orderResult);
		given(keyOrder.keysForOtherParties(any(), any())).willReturn(orderResult);

		given(histories.get(any())).willReturn(recentHistory);

		final com.hederahashgraph.api.proto.java.Transaction signedTxn = mock(com.hederahashgraph.api.proto.java.Transaction.class);
		final TransactionID txnId = mock(TransactionID.class);

		given(txnAccessor.getBackwardCompatibleSignedTxn()).willReturn(signedTxn);
		given(signedTxn.getSignedTransactionBytes()).willReturn(ByteString.EMPTY);
		given(txnAccessor.getTxn()).willReturn(txnBody);
		given(txnBody.getTransactionID()).willReturn(txnId);
		given(txnBody.getTransactionValidDuration()).willReturn(Duration.getDefaultInstance());

		given(recentHistory.currentDuplicityFor(anyLong())).willReturn(BELIEVED_UNIQUE);

		given(txnBody.getNodeAccountID()).willReturn(accountID);
		given(policy.apply(any())).willReturn(ResponseCodeEnum.OK);
		given(policies.check(any())).willReturn(SystemOpAuthorization.AUTHORIZED);
		given(lookup.lookupFor(any(), any())).willReturn(Optional.empty());
		given(ctx.entityAutoRenewal()).willReturn(entityAutoRenewal);

		subject = new AwareProcessLogic(ctx);
	}

	@Test
	void shortCircuitsOnInvariantFailure() throws InvalidProtocolBufferException {
		setupNonTriggeringTxn();

		expandHandleSpan = mock(ExpandHandleSpan.class);

		given(ctx.expandHandleSpan()).willReturn(expandHandleSpan);
		given(expandHandleSpan.accessorFor(platformTxn)).willReturn(new PlatformTxnAccessor(platformTxn));
		given(invariantChecks.holdFor(any(), eq(consensusNow), eq(666L))).willReturn(false);

		// when:
		subject.incorporateConsensusTxn(platformTxn, consensusNow, 666);

		// then:
		verify(expiryManager, never()).purge(consensusNow.getEpochSecond());
	}

	@Test
	void purgesExpiredAtNewConsensusTimeIfInvariantsHold() throws InvalidProtocolBufferException {
		setupNonTriggeringTxn();

		expandHandleSpan = mock(ExpandHandleSpan.class);

		given(ctx.expandHandleSpan()).willReturn(expandHandleSpan);
		given(expandHandleSpan.accessorFor(platformTxn)).willReturn(new PlatformTxnAccessor(platformTxn));
		given(invariantChecks.holdFor(any(), eq(consensusNow), eq(666L))).willReturn(true);

		// when:
		subject.incorporateConsensusTxn(platformTxn, consensusNow, 666);

		// then:
		verify(expiryManager).purge(consensusNow.getEpochSecond());
	}

	@Test
	void decrementsParentConsensusTimeIfCanTrigger() throws InvalidProtocolBufferException {
		setupTriggeringTxn();
		// and:
		final var triggeredTxn = mock(TxnAccessor.class);
		expandHandleSpan = mock(ExpandHandleSpan.class);

		given(ctx.expandHandleSpan()).willReturn(expandHandleSpan);
		given(expandHandleSpan.accessorFor(platformTxn)).willReturn(new PlatformTxnAccessor(platformTxn));
		given(triggeredTxn.isTriggeredTxn()).willReturn(true);
		given(txnCtx.triggeredTxn()).willReturn(triggeredTxn);
		given(invariantChecks.holdFor(any(), eq(consensusNow.minusNanos(1L)), eq(666L))).willReturn(true);

		// when:
		subject.incorporateConsensusTxn(platformTxn, consensusNow, 666);

		// then:
		verify(expiryManager).purge(consensusNow.minusNanos(1L).getEpochSecond());
		verify(triggeredTxn).isTriggeredTxn();
	}

	@Test
	void addForStreamingTest() {
		//setup:
		RecordStreamManager recordStreamManager = mock(RecordStreamManager.class);
		when(ctx.recordStreamManager()).thenReturn(recordStreamManager);

		//when:
		subject.addForStreaming(mock(com.hederahashgraph.api.proto.java.Transaction.class),
				mock(TransactionRecord.class), Instant.now());
		//then:
		verify(ctx).updateRecordRunningHash(any(RunningHash.class));
		verify(recordStreamManager).addRecordStreamObject(any(RecordStreamObject.class));
	}

	private void setupNonTriggeringTxn() {
		TransactionBody nonMockTxnBody = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setAccountID(IdUtils.asAccount("0.0.2"))).build();
		platformTxn = new SwirldTransaction(com.hederahashgraph.api.proto.java.Transaction.newBuilder()
				.setBodyBytes(nonMockTxnBody.toByteString())
				.build().toByteArray());
	}

	private void setupTriggeringTxn() {
		TransactionBody nonMockTxnBody = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setAccountID(IdUtils.asAccount("0.0.2")))
				.setScheduleSign(ScheduleSignTransactionBody.newBuilder()
						.setScheduleID(IdUtils.asSchedule("0.0.1234"))
						.build())
				.build();
		platformTxn = new SwirldTransaction(com.hederahashgraph.api.proto.java.Transaction.newBuilder()
				.setBodyBytes(nonMockTxnBody.toByteString())
				.build().toByteArray());
	}
}
