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
import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.keys.KeysHelper;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
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
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
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

	private AccountID payer = IdUtils.asAccount("1.2.3");
	private ScheduleID schedule = IdUtils.asSchedule("2.4.6");
	private String entityMemo = "some cool memo?";

	private TransactionBody scheduleCreateTxn;
	private InHandleActivationHelper activationHelper;
	private SignatureMap sigMap;
	private Set<JKey> jKeySet;
	private AtomicBoolean returnValid;
	private JKey goodKey = new JEd25519Key("angelic".getBytes());
	private JKey badKey = new JEd25519Key("demonic".getBytes());
	private TransactionSignature validSig, invalidSig;

	private ScheduleCreateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		validator = mock(OptionValidator.class);
		store = mock(ScheduleStore.class);
		accessor = mock(PlatformTxnAccessor.class);
		activationHelper = mock(InHandleActivationHelper.class);

		returnValid = new AtomicBoolean(true);
		validSig = mock(TransactionSignature.class);
		given(validSig.getSignatureStatus()).willReturn(VerificationStatus.VALID);
		given(validSig.getContentsDirect()).willReturn(transactionBody);
		given(validSig.getMessageOffset()).willReturn(0);
		given(validSig.getMessageLength()).willReturn(transactionBody.length);
		invalidSig = mock(TransactionSignature.class);
		given(invalidSig.getSignatureStatus()).willReturn(VerificationStatus.INVALID);
		willAnswer(inv -> {
			BiConsumer<JKey, TransactionSignature> visitor = inv.getArgument(0);
			if (returnValid.get()) {
				visitor.accept(goodKey, validSig);
				returnValid.set(false);
			} else {
				visitor.accept(badKey, invalidSig);
			}
			return null;
		}).given(activationHelper).visitScheduledCryptoSigs(any());

		txnCtx = mock(TransactionContext.class);
		given(txnCtx.activePayer()).willReturn(payer);

		subject = new ScheduleCreateTransitionLogic(store, txnCtx, activationHelper, validator);
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
		ArgumentCaptor<Consumer<MerkleSchedule>> captor = ArgumentCaptor.forClass(Consumer.class);
		MerkleSchedule created = mock(MerkleSchedule.class);
		given(created.transactionBody()).willReturn(transactionBody);

		givenValidTxnCtx();

        given(store.lookupScheduleId(transactionBody, payer)).willReturn(EMPTY_SCHEDULE);
        given(store.createProvisionally(
                eq(transactionBody),
                eq(payer),
                eq(payer),
                eq(RichInstant.fromJava(now)),
                argThat(jKey -> true),
				argThat(memo -> true))).willReturn(CreationResult.success(schedule));
        // and:
        given(store.get(schedule)).willReturn(created);

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(transactionBody, payer);
		// and:
		verify(store).createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((Optional<String> memo) -> memo.get().equals(entityMemo)));
		// and:
		verify(store).apply(argThat(schedule::equals), captor.capture());
		captor.getValue().accept(created);
		verify(created).witnessValidEd25519Signature(goodKey.getEd25519());
		verify(created, never()).witnessValidEd25519Signature(badKey.getEd25519());

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
		given(store.lookupScheduleId(transactionBody, payer)).willReturn(Optional.of(schedule));
		given(store.get(schedule)).willReturn(created);

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(transactionBody, payer);
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
	public void capturesFailingCreateProvisionally() {
		// given:
		givenValidTxnCtx();

		// and:
		given(store.lookupScheduleId(transactionBody, payer)).willReturn(EMPTY_SCHEDULE);
		given(store.createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				argThat(jKey -> true),
				argThat(memo -> true)))
				.willReturn(CreationResult.failure(INVALID_ADMIN_KEY));

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(transactionBody, payer);
		// and:
		verify(store).createProvisionally(
				eq(transactionBody),
				eq(payer),
				eq(payer),
				eq(RichInstant.fromJava(now)),
				argThat((Optional<JKey> k) -> equalUpToDecodability(k.get(), jAdminKey.get())),
				argThat((Optional<String> m) -> m.get().equals(entityMemo)));
		verify(store, never()).addSigners(schedule, jKeySet);
		verify(store, never()).commitCreation();
		verify(txnCtx, never()).setStatus(SUCCESS);
	}

	@Test
	public void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx();
		// and:
		given(store.lookupScheduleId(transactionBody, payer)).willThrow(IllegalArgumentException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(store).lookupScheduleId(transactionBody, payer);
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

		try {
			jAdminKey = asUsableFcKey(key);
			jKeySet = new HashSet<>();
			for (SignaturePair signaturePair : this.sigMap.getSigPairList()) {
				jKeySet.add(KeysHelper.ed25519ToJKey(signaturePair.getPubKeyPrefix()));
			}
		} catch (DecoderException e) {
			e.printStackTrace();
		}

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
	}
}
