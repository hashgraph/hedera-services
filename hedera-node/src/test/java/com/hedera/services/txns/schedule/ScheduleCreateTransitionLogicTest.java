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
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleSchedule;
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
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
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

	private final Optional<ScheduleID> EMPTY_SCHEDULE = Optional.empty();
	private final Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	private final Key invalidKey = Key.newBuilder().build();
	private Optional<JKey> jAdminKey;

	private OptionValidator validator;
	private ScheduleStore store;
	private PlatformTxnAccessor accessor;
	private TransactionContext txnCtx;
	private SignatoryUtils.SigningsWitness signingsWitness;
	private ScheduleReadyForExecution.ExecutionProcessor executor;

	private AccountID payer = IdUtils.asAccount("1.2.3");
	private ScheduleID schedule = IdUtils.asSchedule("2.4.6");
	private String entityMemo = "some cool memo?";

	private TransactionBody scheduleCreateTxn;
	private InHandleActivationHelper activationHelper;
	private SignatureMap sigMap;

	private ScheduleCreateTransitionLogic subject;

	@BeforeEach
	private void setup() throws InvalidProtocolBufferException {
		validator = mock(OptionValidator.class);
		store = mock(ScheduleStore.class);
		accessor = mock(PlatformTxnAccessor.class);
		activationHelper = mock(InHandleActivationHelper.class);
		signingsWitness = mock(SignatoryUtils.SigningsWitness.class);
		executor = mock(ScheduleReadyForExecution.ExecutionProcessor.class);

		given(signingsWitness.observeInScope(1, schedule, store, activationHelper))
				.willReturn(Pair.of(OK, true));
		given(executor.doProcess(schedule)).willReturn(OK);

		txnCtx = mock(TransactionContext.class);
		given(txnCtx.activePayer()).willReturn(payer);

		subject = new ScheduleCreateTransitionLogic(store, txnCtx, activationHelper, validator);
		subject.signingsWitness = signingsWitness;
		subject.executor = executor;
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
		given(created.transactionBody()).willReturn(transactionBody);

		givenValidTxnCtx();
		// and:
		given(store.get(schedule)).willReturn(created);

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(
				eq(transactionBody),
				eq(payer),
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((String memo) -> Objects.equals(memo, entityMemo)));

		// and:
		verify(store).createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((Optional<String> memo) -> memo.get().equals(entityMemo)));
		// and:
		verify(signingsWitness).observeInScope(1, schedule, store, activationHelper);
		// and:
		verify(store).commitCreation();
		verify(txnCtx).setStatus(SUCCESS);
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
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
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
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((String memo) -> Objects.equals(memo, entityMemo)));

		// and:
		verify(store, never()).createProvisionally(eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				argThat(jKey -> true),
				argThat(memo -> true));
		// and:
		verify(store, never()).commitCreation();
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void rollsBackForAnyNonOkSigning() throws InvalidProtocolBufferException {
		// given:
		givenValidTxnCtx();
		// and:
		given(signingsWitness.observeInScope(1, schedule, store, activationHelper))
				.willReturn(Pair.of(SOME_SIGNATURES_WERE_INVALID, true));

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(
				eq(transactionBody),
				eq(payer),
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((String memo) -> Objects.equals(memo, entityMemo)));

		// and:
		verify(store).createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
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
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((String m) -> m.equals(entityMemo)))).willReturn(EMPTY_SCHEDULE);
		given(store.createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				argThat(jKey -> true),
				argThat(memo -> true))).willReturn(CreationResult.failure(INVALID_ADMIN_KEY));

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(
				eq(transactionBody),
				eq(payer),
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((String memo) -> Objects.equals(memo, entityMemo)));

		// and:
		verify(store).createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
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
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((String m) -> m.equals(entityMemo))))
				.willThrow(IllegalArgumentException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(
				eq(transactionBody),
				eq(payer),
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((String memo) -> Objects.equals(memo, entityMemo)));
		// and:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	public void failsOnInvalidAdminKey() {
		givenCtx(
				true,
				false,
				false);

		// expect:
		assertEquals(INVALID_ADMIN_KEY, subject.validate(scheduleCreateTxn));
	}

	@Test
	public void failsOnInvalidMemo() {
		givenCtx(
				false,
				false,
				true);

		// expect:
		assertEquals(MEMO_TOO_LONG, subject.validate(scheduleCreateTxn));
	}

	@Test
	public void acceptsValidTxn() {
		givenValidTxnCtx();

		assertEquals(OK, subject.syntaxCheck().apply(scheduleCreateTxn));
	}

	@Test
	public void rejectsInvalidAdminKey() {
		givenCtx(true, false, false);

		assertEquals(INVALID_ADMIN_KEY, subject.syntaxCheck().apply(scheduleCreateTxn));
	}

	private void givenValidTxnCtx() {
		givenCtx(
				false,
				false,
				false);
	}

	private void givenCtx(
			boolean invalidAdminKey,
			boolean invalidPubKey,
			boolean invalidMemo
	) {
		var pair = new KeyPairGenerator().generateKeyPair();
		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		if (invalidPubKey) {
			pubKey = "asd".getBytes();
		}
		this.sigMap = SignatureMap.newBuilder().addSigPair(
				SignaturePair.newBuilder()
						.setPubKeyPrefix(ByteString.copyFrom(pubKey))
		).build();

		jAdminKey = asUsableFcKey(key);

		var builder = TransactionBody.newBuilder();
		var scheduleCreate = ScheduleCreateTransactionBody.newBuilder()
				.setSigMap(sigMap)
				.setAdminKey(key)
				.setPayerAccountID(payer)
				.setMemo(entityMemo)
				.setTransactionBody(ByteString.copyFrom(transactionBody));

		if (invalidAdminKey) {
			scheduleCreate.setAdminKey(invalidKey);
		}
		builder.setScheduleCreate(scheduleCreate);

		this.scheduleCreateTxn = builder.build();

		given(validator.isValidEntityMemo(entityMemo)).willReturn(!invalidMemo);
		given(accessor.getTxn()).willReturn(this.scheduleCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.activePayer()).willReturn(payer);
		given(txnCtx.consensusTime()).willReturn(now);
		given(store.isCreationPending()).willReturn(true);
		given(store.lookupScheduleId(transactionBody, payer, jAdminKey, entityMemo)).willReturn(EMPTY_SCHEDULE);
		given(store.createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				argThat(jKey -> true),
				argThat(memo -> true))).willReturn(CreationResult.success(schedule));
	}
}
