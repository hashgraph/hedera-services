package com.hedera.services.txns.schedule;

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
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.txns.schedule.SigMapScheduleClassifierTest.pretendKeyStartingWith;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_EXISTS_WITH_DIFFERENT_PAYER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ScheduleCreateTransitionLogicTest {
	private static final long thisSecond = 1_234_567L;
	private static final Instant now = Instant.ofEpochSecond(thisSecond);
	private static final byte[] bodyBytes = TransactionBody.newBuilder()
			.setMemo("Just this")
			.build()
			.toByteArray();
	private static final TransactionID scheduledTxnId = TransactionID.newBuilder()
			.setAccountID(IdUtils.asAccount("0.0.2"))
			.setScheduled(true)
			.build();

	private static final Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	private static final Key invalidKey = Key.getDefaultInstance();

	private static final AccountID payer = IdUtils.asAccount("1.2.3");
	private static final EntityId payerId = EntityId.fromGrpcAccountId(payer);
	private static final EntityId anotherPayerId = new EntityId(1,2,4);
	private static final ScheduleID schedule = IdUtils.asSchedule("2.4.6");
	private static final String entityMemo = "some cool memo?";
	private static final String innerMemo = "Strictly business now";

	private static final TransactionID txnId = TransactionID.newBuilder()
			.setTransactionValidStart(
					Timestamp.newBuilder()
							.setSeconds(now.getEpochSecond())
							.setNanos(now.getNano()).build()
			).build();

	private static final JKey payerKey = new JEd25519Key(pretendKeyStartingWith("payer"));
	private static final Optional<JKey> jAdminKey = asUsableFcKey(key);
	private static final Optional<List<JKey>> validScheduleKeys = Optional.of(
			List.of(new JEd25519Key(pretendKeyStartingWith("scheduled"))));
	private static final SignatureMap sigMap = SigMapScheduleClassifierTest.sigMap;

	private boolean adminKeyActuallySkipped = false;
	private boolean invalidAdminKeyIsSentinelKeyList = false;

	private OptionValidator validator;
	private ScheduleStore store;
	private MerkleSchedule merkleSchedule;
	private PlatformTxnAccessor accessor;
	private TransactionContext txnCtx;
	private ScheduleExecutor executor;

	private TransactionBody scheduleCreateTxn;
	private InHandleActivationHelper activationHelper;
	private MockedStatic<ScheduleSignHelper> scheduleSignHelper;

	private ScheduleCreateTransitionLogic subject;

	@BeforeEach
	private void setup() throws InvalidProtocolBufferException {
		validator = mock(OptionValidator.class);
		store = mock(ScheduleStore.class);
		accessor = mock(PlatformTxnAccessor.class);
		activationHelper = mock(InHandleActivationHelper.class);
		executor = mock(ScheduleExecutor.class);
		merkleSchedule = mock(MerkleSchedule.class);
		scheduleSignHelper = mockStatic(ScheduleSignHelper.class);
		scheduleSignHelper.when(() -> ScheduleSignHelper.signingOutcome(
				any(), any(), any(), any(), any())).thenReturn(Pair.of(OK, true));

		given(accessor.getTxnBytes()).willReturn(bodyBytes);
		given(executor.processExecution(any(), any(), any())).willReturn(OK);

		txnCtx = mock(TransactionContext.class);
		given(txnCtx.activePayer()).willReturn(payer);
		given(txnCtx.activePayerKey()).willReturn(payerKey);

		subject = new ScheduleCreateTransitionLogic(store, txnCtx, activationHelper, validator, executor);
	}

	@AfterEach
	void tearDown() {
		scheduleSignHelper.close();
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		assertTrue(subject.applicability().test(scheduleCreateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void followsHappyPath() {
		given(merkleSchedule.scheduledTransactionId()).willReturn(scheduledTxnId);
		given(merkleSchedule.expiry()).willReturn(now.getEpochSecond());
		givenValidTxnCtx();
		given(merkleSchedule.adminKey()).willReturn(jAdminKey);

		subject.doStateTransition();

		verify(store).lookupSchedule(bodyBytes);
		verify(store).createProvisionally(merkleSchedule, RichInstant.fromJava(now));
		verify(store).commitCreation();
		verify(txnCtx).addExpiringEntities(any());
		verify(txnCtx).setStatus(SUCCESS);
		verify(txnCtx).setScheduledTxnId(scheduledTxnId);
	}

	@Test
	void followsHappyPathEvenIfNoNewValidSignatures() {
		given(merkleSchedule.scheduledTransactionId()).willReturn(scheduledTxnId);
		given(merkleSchedule.expiry()).willReturn(now.getEpochSecond());
		givenValidTxnCtx();
		given(merkleSchedule.adminKey()).willReturn(jAdminKey);
		scheduleSignHelper.when(() -> ScheduleSignHelper.signingOutcome(
				any(), any(), any(), any(), any())).thenReturn(Pair.of(NO_NEW_VALID_SIGNATURES, false));

		subject.doStateTransition();

		verify(store).commitCreation();
		verify(txnCtx).addExpiringEntities(any());
		verify(txnCtx).setStatus(SUCCESS);
		verify(txnCtx).setScheduledTxnId(scheduledTxnId);
	}

	@Test
	void rejectsRecreationOfExistingScheduleIfMergeWithIdenticalScheduleSetFalse() {
		givenValidTxnCtx();
		given(merkleSchedule.scheduledTransactionId()).willReturn(scheduledTxnId);
		given(merkleSchedule.isMergeWithIdenticalSchedule()).willReturn(false);
		given(store.lookupSchedule(bodyBytes)).willReturn(Pair.of(schedule, merkleSchedule));

		subject.doStateTransition();

		verify(store, never()).createProvisionally(any(), any());
		verify(store, never()).commitCreation();
		verify(txnCtx, never()).addExpiringEntities(any());
		verify(txnCtx).setStatus(IDENTICAL_SCHEDULE_ALREADY_CREATED);
		verify(txnCtx).setCreated(schedule);
		verify(txnCtx).setScheduledTxnId(scheduledTxnId);
	}

	@Test
	void addsSignatureToExistingScheduleIfMergeWithIdenticalScheduleSetTrue() {
		givenValidTxnCtx();
		given(merkleSchedule.scheduledTransactionId()).willReturn(scheduledTxnId);
		given(merkleSchedule.isMergeWithIdenticalSchedule()).willReturn(true);
		given(merkleSchedule.payer()).willReturn(payerId);
		given(store.lookupSchedule(bodyBytes)).willReturn(Pair.of(schedule, merkleSchedule));

		subject.doStateTransition();

		verify(store, never()).createProvisionally(any(), any());
		verify(store, never()).commitCreation();
		verify(txnCtx, never()).addExpiringEntities(any());
		verify(txnCtx).setStatus(SUCCESS);
		verify(txnCtx).setCreated(schedule);
		verify(txnCtx).setScheduledTxnId(scheduledTxnId);
	}

	@Test
	void rejectsRecreationIfPayersNotSame() {
		givenValidTxnCtx();
		given(merkleSchedule.scheduledTransactionId()).willReturn(scheduledTxnId);
		given(merkleSchedule.isMergeWithIdenticalSchedule()).willReturn(true);
		given(merkleSchedule.payer()).willReturn(anotherPayerId);
		given(store.lookupSchedule(bodyBytes)).willReturn(Pair.of(schedule, merkleSchedule));

		subject.doStateTransition();

		verify(store, never()).createProvisionally(any(), any());
		verify(store, never()).commitCreation();
		verify(txnCtx, never()).addExpiringEntities(any());
		verify(txnCtx).setStatus(IDENTICAL_SCHEDULE_ALREADY_EXISTS_WITH_DIFFERENT_PAYER);
		verify(txnCtx).setCreated(schedule);
		verify(txnCtx).setScheduledTxnId(scheduledTxnId);
	}

	@Test
	void rejectsRecreationForNonOkSigning() {
		givenValidTxnCtx();
		given(merkleSchedule.scheduledTransactionId()).willReturn(scheduledTxnId);
		given(merkleSchedule.isMergeWithIdenticalSchedule()).willReturn(true);
		given(merkleSchedule.payer()).willReturn(payerId);
		given(store.lookupSchedule(bodyBytes)).willReturn(Pair.of(schedule, merkleSchedule));
		scheduleSignHelper.when(() -> ScheduleSignHelper.signingOutcome(
				any(), any(), any(), any(), any())).thenReturn(Pair.of(SOME_SIGNATURES_WERE_INVALID, true));

		subject.doStateTransition();

		verify(store, never()).createProvisionally(any(), any());
		verify(store, never()).commitCreation();
		verify(txnCtx, never()).addExpiringEntities(any());
		verify(txnCtx).setStatus(SOME_SIGNATURES_WERE_INVALID);
	}

	@Test
	void rollsBackForAnyNonOkSigning() throws InvalidProtocolBufferException {
		givenValidTxnCtx();
		given(merkleSchedule.adminKey()).willReturn(jAdminKey);
		scheduleSignHelper.when(() -> ScheduleSignHelper.signingOutcome(
				any(), any(), any(), any(), any())).thenReturn(Pair.of(SOME_SIGNATURES_WERE_INVALID, true));

		subject.doStateTransition();

		verify(store).createProvisionally(merkleSchedule, RichInstant.fromJava(now));
		verify(store, never()).commitCreation();
		verify(txnCtx).setStatus(SOME_SIGNATURES_WERE_INVALID);
		verify(executor, never()).processExecution(schedule, store, txnCtx);
	}

	@Test
	void capturesFailingCreateProvisionally() {
		givenValidTxnCtx();
		given(store.lookupSchedule(bodyBytes)).willReturn(Pair.of(null, merkleSchedule));
		given(store.createProvisionally(merkleSchedule, RichInstant.fromJava(now)))
				.willReturn(CreationResult.failure(INVALID_ADMIN_KEY));

		subject.doStateTransition();

		verify(store, never()).commitCreation();
		verify(txnCtx, never()).setStatus(SUCCESS);
	}

	@Test
	void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx();
		given(store.lookupSchedule(bodyBytes)).willThrow(IllegalArgumentException.class);

		subject.doStateTransition();

		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	void failsOnAdminKeySetAsSentinelKeylist() {
		invalidAdminKeyIsSentinelKeyList = true;
		givenCtx(true, false, false);

		assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(scheduleCreateTxn));
	}

	@Test
	void syntaxOkWithNoAdminKey() {
		adminKeyActuallySkipped = true;
		givenValidTxnCtx();

		assertEquals(OK, subject.semanticCheck().apply(scheduleCreateTxn));
	}

	@Test
	void failsOnInvalidAdminKey() {
		givenCtx(true, false, false);

		assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(scheduleCreateTxn));
	}

	@Test
	void failsOnInvalidEntityMemo() {
		givenCtx(false, true, false);

		assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.semanticCheck().apply(scheduleCreateTxn));
	}

	@Test
	void failsOnInvalidInnerMemo() {
		givenCtx(false, false, true);

		assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.semanticCheck().apply(scheduleCreateTxn));
	}

	@Test
	void acceptsValidTxn() {
		givenValidTxnCtx();

		assertEquals(OK, subject.semanticCheck().apply(scheduleCreateTxn));
	}

	private void givenValidTxnCtx() {
		givenCtx(false, false, false);
	}

	private void givenCtx(
			final boolean invalidAdminKey,
			final boolean invalidEntityMemo,
			final boolean invalidInnerMemo
	) {
		given(accessor.getSigMap()).willReturn(sigMap);

		final var builder = TransactionBody.newBuilder();
		final var scheduleCreate = ScheduleCreateTransactionBody.newBuilder()
				.setAdminKey(key)
				.setPayerAccountID(payer)
				.setMemo(entityMemo)
				.setScheduledTransactionBody(
						SchedulableTransactionBody.newBuilder().setMemo(innerMemo));

		if (invalidAdminKey) {
			if (invalidAdminKeyIsSentinelKeyList) {
				scheduleCreate.setAdminKey(ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY);
			} else {
				scheduleCreate.setAdminKey(invalidKey);
			}
		}

		if (adminKeyActuallySkipped) {
			scheduleCreate.clearAdminKey();
		}

		builder.setTransactionID(txnId);
		builder.setScheduleCreate(scheduleCreate);

		scheduleCreateTxn = builder.build();

		given(validator.memoCheck(entityMemo)).willReturn(invalidEntityMemo ? INVALID_ZERO_BYTE_IN_STRING : OK);
		given(validator.memoCheck(innerMemo)).willReturn(invalidInnerMemo ? INVALID_ZERO_BYTE_IN_STRING : OK);
		given(accessor.getTxnId()).willReturn(txnId);
		given(accessor.getTxn()).willReturn(scheduleCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.activePayer()).willReturn(payer);
		given(txnCtx.consensusTime()).willReturn(now);
		given(store.isCreationPending()).willReturn(true);
		given(store.lookupSchedule(bodyBytes)).willReturn(Pair.of(null, merkleSchedule));
		given(store.createProvisionally(merkleSchedule, RichInstant.fromJava(now)))
				.willReturn(CreationResult.success(schedule));
	}
}
