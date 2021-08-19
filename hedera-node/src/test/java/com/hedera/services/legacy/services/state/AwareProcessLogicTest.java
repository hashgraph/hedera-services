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
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.security.ops.SystemOpAuthorization;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.expiry.EntityAutoRenewal;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.logic.InvariantChecks;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.stream.NonBlockingHandoff;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

class AwareProcessLogicTest {
	private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L);

	private SwirldTransaction platformTxn;
	private InvariantChecks invariantChecks;
	private ServicesContext ctx;
	private ExpiryManager expiryManager;
	private TransactionContext txnCtx;
	private ExpandHandleSpan expandHandleSpan;
	private NonBlockingHandoff nonBlockingHandoff;
	private PlatformTxnAccessor txnAccessor;
	private Rationalization rationalization;
	private HederaSigningOrder keyOrder;
	private MiscSpeedometers speedometers;
	private BiPredicate<JKey, TransactionSignature> validityTest;

	private AwareProcessLogic subject;

	@BeforeEach
	void setup() {
		final SwirldTransaction txn = mock(SwirldTransaction.class);
		final HederaLedger ledger = mock(HederaLedger.class);
		final AccountRecordsHistorian historian = mock(AccountRecordsHistorian.class);
		final SigningOrderResult orderResult = mock(SigningOrderResult.class);
		final MiscRunningAvgs runningAvgs = mock(MiscRunningAvgs.class);
		final FeeCalculator fees = mock(FeeCalculator.class);
		final TxnIdRecentHistory recentHistory = mock(TxnIdRecentHistory.class);
		final Map<TransactionID, TxnIdRecentHistory> histories = mock(Map.class);
		final AccountID accountID = mock(AccountID.class);
		final FeeChargingPolicy policy = mock(FeeChargingPolicy.class);
		final SystemOpPolicies policies = mock(SystemOpPolicies.class);
		final TransitionLogicLookup lookup = mock(TransitionLogicLookup.class);
		final EntityAutoRenewal entityAutoRenewal = mock(EntityAutoRenewal.class);

		speedometers = mock(MiscSpeedometers.class);
		invariantChecks = mock(InvariantChecks.class);
		expiryManager = mock(ExpiryManager.class);
		keyOrder = mock(HederaSigningOrder.class);
		txnCtx = mock(TransactionContext.class);
		txnAccessor = mock(PlatformTxnAccessor.class);
		rationalization = mock(Rationalization.class);
		validityTest = (BiPredicate<JKey, TransactionSignature>) mock(BiPredicate.class);

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

		final com.hederahashgraph.api.proto.java.Transaction signedTxn = mock(
				com.hederahashgraph.api.proto.java.Transaction.class);
		final TransactionID txnId = mock(TransactionID.class);

		given(txnAccessor.getSignedTxnWrapper()).willReturn(signedTxn);
		given(signedTxn.getSignedTransactionBytes()).willReturn(ByteString.EMPTY);
		given(txnAccessor.getTxn()).willReturn(txnBody);
		given(txnBody.getTransactionID()).willReturn(txnId);
		given(txnBody.getTransactionValidDuration()).willReturn(Duration.getDefaultInstance());

		given(recentHistory.currentDuplicityFor(anyLong())).willReturn(BELIEVED_UNIQUE);

		given(txnBody.getNodeAccountID()).willReturn(accountID);
		given(policy.apply(any())).willReturn(OK);
		given(policies.check(any())).willReturn(SystemOpAuthorization.AUTHORIZED);
		given(lookup.lookupFor(any(), any())).willReturn(Optional.empty());
		given(ctx.entityAutoRenewal()).willReturn(entityAutoRenewal);

		subject = new AwareProcessLogic(ctx, rationalization, validityTest);
	}

	@Test
	void cyclesAsyncVerificationWhenAppropros() {
		// setup:
		final var sigBytesFn = mock(PubKeyToSigBytes.class);
		final var syncVerifier = mock(SyncVerifier.class);

		given(ctx.syncVerifier()).willReturn(syncVerifier);
		given(txnAccessor.getPkToSigsFn()).willReturn(sigBytesFn);
		given(rationalization.finalStatus()).willReturn(OK);

		// when:
		final var result = subject.rationalizeWithPreConsensusSigs(txnAccessor);

		// then:
		verify(rationalization).performFor(txnAccessor);
		verify(speedometers).cycleAsyncVerifications();
		// and:
		Assertions.assertEquals(OK, result);
	}

	@Test
	void cyclesSyncVerificationWhenAppropros() {
		// setup:
		final var sigBytesFn = mock(PubKeyToSigBytes.class);
		final var syncVerifier = mock(SyncVerifier.class);

		given(ctx.syncVerifier()).willReturn(syncVerifier);
		given(txnAccessor.getPkToSigsFn()).willReturn(sigBytesFn);
		given(rationalization.finalStatus()).willReturn(OK);
		given(rationalization.usedSyncVerification()).willReturn(true);

		// when:
		final var result = subject.rationalizeWithPreConsensusSigs(txnAccessor);

		// then:
		verify(rationalization).performFor(txnAccessor);
		verify(speedometers).cycleSyncVerifications();
		// and:
		Assertions.assertEquals(OK, result);
	}

	@Test
	void noVerificationCyclesWhenRationalizationNotOk() {
		// setup:
		final var sigBytesFn = mock(PubKeyToSigBytes.class);
		final var syncVerifier = mock(SyncVerifier.class);

		given(ctx.syncVerifier()).willReturn(syncVerifier);
		given(txnAccessor.getPkToSigsFn()).willReturn(sigBytesFn);
		given(rationalization.finalStatus()).willReturn(INVALID_ACCOUNT_ID);

		// when:
		final var result = subject.rationalizeWithPreConsensusSigs(txnAccessor);

		// then:
		verify(rationalization).performFor(txnAccessor);
		verify(rationalization, never()).usedSyncVerification();
		verifyNoInteractions(speedometers);
		// and:
		Assertions.assertEquals(INVALID_ACCOUNT_ID, result);
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
		// setup:
		nonBlockingHandoff = mock(NonBlockingHandoff.class);
		given(nonBlockingHandoff.offer(any())).willReturn(true);
		given(ctx.nonBlockingHandoff()).willReturn(nonBlockingHandoff);

		RecordStreamManager recordStreamManager = mock(RecordStreamManager.class);
		when(ctx.recordStreamManager()).thenReturn(recordStreamManager);

		// when:
		subject.stream(
				mock(com.hederahashgraph.api.proto.java.Transaction.class),
				mock(ExpirableTxnRecord.class),
				Instant.now());

		// then:
		verify(ctx).updateRecordRunningHash(any(RunningHash.class));
		verify(nonBlockingHandoff).offer(any());
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
