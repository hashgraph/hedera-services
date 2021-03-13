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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
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
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.txns.schedule.SigMapScheduleClassifierTest.pretendKeyStartingWith;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ScheduleCreateTransitionLogicTest {
	long thisSecond = 1_234_567L;
	private Instant now = Instant.ofEpochSecond(thisSecond);
	private byte[] transactionBody = TransactionBody.newBuilder()
			.setMemo("Just this")
			.build()
			.toByteArray();
	final TransactionID scheduledTxnId = TransactionID.newBuilder()
			.setAccountID(IdUtils.asAccount("0.0.2"))
			.setScheduled(true)
			.build();

	private final Optional<ScheduleID> EMPTY_SCHEDULE = Optional.empty();
	private final Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	private final Key invalidKey = Key.newBuilder().build();
	private Optional<JKey> jAdminKey;

	private OptionValidator validator;
	private ScheduleStore store;
	private PlatformTxnAccessor accessor;
	private TransactionContext txnCtx;
	private SignatoryUtils.ScheduledSigningsWitness replSigningWitness;
	private ScheduleReadyForExecution.ExecutionProcessor executor;

	private AccountID payer = IdUtils.asAccount("1.2.3");
	private ScheduleID schedule = IdUtils.asSchedule("2.4.6");
	private String entityMemo = "some cool memo?";

	private TransactionID txnId;
	private TransactionBody scheduleCreateTxn;
	private InHandleActivationHelper activationHelper;
	private SignatureMap sigMap = SigMapScheduleClassifierTest.sigMap;

	private JKey payerKey = new JEd25519Key(pretendKeyStartingWith("payer"));
	private SigMapScheduleClassifier classifier;
	private Optional<List<JKey>> validScheduleKeys = Optional.of(
			List.of(new JEd25519Key(pretendKeyStartingWith("scheduled"))));

	private ScheduleCreateTransitionLogic subject;

	@BeforeEach
	private void setup() throws InvalidProtocolBufferException {
		validator = mock(OptionValidator.class);
		store = mock(ScheduleStore.class);
		accessor = mock(PlatformTxnAccessor.class);
		activationHelper = mock(InHandleActivationHelper.class);
		replSigningWitness = mock(SignatoryUtils.ScheduledSigningsWitness.class);
		executor = mock(ScheduleReadyForExecution.ExecutionProcessor.class);

		classifier = mock(SigMapScheduleClassifier.class);

		given(replSigningWitness.observeInScope(schedule, store, validScheduleKeys, activationHelper))
				.willReturn(Pair.of(OK, true));

		given(executor.doProcess(schedule)).willReturn(OK);

		txnCtx = mock(TransactionContext.class);
		given(txnCtx.activePayer()).willReturn(payer);
		given(txnCtx.activePayerKey()).willReturn(payerKey);

		subject = new ScheduleCreateTransitionLogic(store, txnCtx, activationHelper, validator);

		subject.signingsWitness = replSigningWitness;
		subject.executor = executor;
		subject.classifier = classifier;
	}

	@Test
	public void validProcessExecution() throws InvalidProtocolBufferException {
		// setup:
		givenValidTxnCtx();
		// and:
		MerkleSchedule created = mock(MerkleSchedule.class);
		given(created.payer()).willReturn(EntityId.ofNullableAccountId(payer));
		given(created.asScheduledTransaction()).willReturn(Transaction.getDefaultInstance());
		given(store.get(schedule)).willReturn(created);
		// and:
		given(store.markAsExecuted(schedule)).willReturn(OK);

		// when:
		var result = subject.processExecution(schedule);

		// then:
		verify(store).get(schedule);
		verify(txnCtx).trigger(any());
		verify(store).markAsExecuted(schedule);
		// and:
		assertEquals(OK, result);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(scheduleCreateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void followsHappyPath() {
		// setup:
		MerkleSchedule created = mock(MerkleSchedule.class);
		given(created.scheduledTransactionId()).willReturn(scheduledTxnId);
		given(created.transactionBody()).willReturn(transactionBody);
		given(created.expiry()).willReturn(now.getEpochSecond());

		givenValidTxnCtx();
		// and:
		given(store.get(schedule)).willReturn(created);

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(
				eq(transactionBody),
				eq(payer),
				eq(key),
				argThat((String memo) -> Objects.equals(memo, entityMemo)));

		// and:
		verify(store).createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				eq(RichInstant.fromJava(now)),
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((Optional<String> memo) -> memo.get().equals(entityMemo)));
		// and:
		verify(replSigningWitness).observeInScope(schedule, store, validScheduleKeys, activationHelper);
		// and:
		verify(store).commitCreation();
		verify(txnCtx).addExpiringEntities(any());
		verify(txnCtx).setStatus(SUCCESS);
		verify(txnCtx).setScheduledTxnId(scheduledTxnId);
	}

	@Test
	public void capturesPendingScheduledTransaction() {
		// given:
		givenValidTxnCtx();

		MerkleSchedule created = mock(MerkleSchedule.class);
		given(created.transactionBody()).willReturn(transactionBody);

		// and:
		given(store.lookupScheduleId(
				eq(transactionBody),
				eq(payer),
				eq(key),
				argThat((String m) -> m.equals(entityMemo))))
				.willReturn(Optional.of(schedule));
		given(store.get(schedule)).willReturn(created);
		given(store.isCreationPending()).willReturn(false);

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(
				eq(transactionBody),
				eq(payer),
				eq(key),
				argThat((String memo) -> Objects.equals(memo, entityMemo)));

		// and:
		verify(store, never()).createProvisionally(eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				eq(RichInstant.fromJava(now)),
				argThat(jKey -> true),
				argThat(memo -> true));
		// and:
		verify(store, never()).commitCreation();
		verify(txnCtx, never()).addExpiringEntities(any());
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void rollsBackForAnyNonOkSigning() throws InvalidProtocolBufferException {
		// given:
		givenValidTxnCtx();
		// and:
		given(replSigningWitness.observeInScope(schedule, store, validScheduleKeys, activationHelper))
				.willReturn(Pair.of(SOME_SIGNATURES_WERE_INVALID, true));

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(
				eq(transactionBody),
				eq(payer),
				eq(key),
				argThat((String memo) -> Objects.equals(memo, entityMemo)));

		// and:
		verify(store).createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				eq(RichInstant.fromJava(now)),
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((Optional<String> m) -> m.get().equals(entityMemo)));
		verify(store, never()).commitCreation();
		verify(txnCtx).setStatus(SOME_SIGNATURES_WERE_INVALID);
		verify(executor, never()).doProcess(any());
	}

	@Test
	public void capturesFailingCreateProvisionally() {
		// given:
		givenValidTxnCtx();

		// and:
		given(store.lookupScheduleId(
				eq(transactionBody),
				eq(payer),
				eq(key),
				argThat((String m) -> m.equals(entityMemo)))).willReturn(EMPTY_SCHEDULE);
		given(store.createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				eq(RichInstant.fromJava(now)),
				argThat(jKey -> true),
				argThat(memo -> true))).willReturn(CreationResult.failure(INVALID_ADMIN_KEY));

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(
				eq(transactionBody),
				eq(payer),
				eq(key),
				argThat((String memo) -> Objects.equals(memo, entityMemo)));

		// and:
		verify(store).createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				eq(RichInstant.fromJava(now)),
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((Optional<String> m) -> m.get().equals(entityMemo)));
		verify(store, never()).commitCreation();
		verify(txnCtx, never()).setStatus(SUCCESS);
	}

	@Test
	public void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx();
		// and:
		given(store.lookupScheduleId(
				eq(transactionBody),
				eq(payer),
				eq(key),
				argThat((String m) -> m.equals(entityMemo))))
				.willThrow(IllegalArgumentException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(
				eq(transactionBody),
				eq(payer),
				eq(key),
				argThat((String memo) -> Objects.equals(memo, entityMemo)));
		// and:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	public void failsOnInvalidAdminKey() {
		givenCtx(
				true,
				false);

		// expect:
		assertEquals(INVALID_ADMIN_KEY, subject.syntaxCheck().apply(scheduleCreateTxn));
	}

	@Test
	public void failsOnInvalidMemo() {
		givenCtx(
				false,
				true);

		// expect:
		assertEquals(MEMO_TOO_LONG, subject.syntaxCheck().apply(scheduleCreateTxn));
	}

	@Test
	public void acceptsValidTxn() {
		givenValidTxnCtx();

		assertEquals(OK, subject.syntaxCheck().apply(scheduleCreateTxn));
	}

	private void givenValidTxnCtx() {
		givenCtx(
				false,
				false);
	}

	private void givenCtx(
			boolean invalidAdminKey,
			boolean invalidMemo
	) {
		given(accessor.getSigMap()).willReturn(sigMap);
		given(classifier.validScheduleKeys(eq(payerKey), eq(sigMap), any(), any())).willReturn(validScheduleKeys);

		jAdminKey = asUsableFcKey(key);

		txnId = TransactionID.newBuilder()
				.setTransactionValidStart(
						Timestamp.newBuilder()
								.setSeconds(now.getEpochSecond())
								.setNanos(now.getNano()).build()
				).build();

		var builder = TransactionBody.newBuilder();
		var scheduleCreate = ScheduleCreateTransactionBody.newBuilder()
				.setAdminKey(key)
				.setPayerAccountID(payer)
				.setMemo(entityMemo)
				.setTransactionBody(ByteString.copyFrom(transactionBody));

		if (invalidAdminKey) {
			scheduleCreate.setAdminKey(invalidKey);
		}
		builder.setTransactionID(txnId);
		builder.setScheduleCreate(scheduleCreate);

		scheduleCreateTxn = builder.build();

		given(validator.isValidEntityMemo(entityMemo)).willReturn(!invalidMemo);
		given(accessor.getTxnId()).willReturn(txnId);
		given(accessor.getTxn()).willReturn(scheduleCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.activePayer()).willReturn(payer);
		given(txnCtx.consensusTime()).willReturn(now);
		given(store.isCreationPending()).willReturn(true);
		given(store.lookupScheduleId(transactionBody, payer, key, entityMemo)).willReturn(EMPTY_SCHEDULE);
		given(store.createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				eq(RichInstant.fromJava(now)),
				argThat(jKey -> true),
				argThat(memo -> true))).willReturn(CreationResult.success(schedule));
	}
}
