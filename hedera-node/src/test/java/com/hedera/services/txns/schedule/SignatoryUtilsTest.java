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

import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test scenarios are for the simplest case of one required signature.
 */
class SignatoryUtilsTest {
	private ScheduleID id = IdUtils.asSchedule("0.0.75231");
	private ScheduleStore store;
	private MerkleSchedule schedule;

	private InHandleActivationHelper activationHelper;
	private SignatureMap sigMap;
	private JKey goodKey = new JEd25519Key("angelic".getBytes());
	private JKey badKey = new JEd25519Key("demonic".getBytes());
	private TransactionSignature validSig, invalidSig;

	@BeforeEach
	void setUp() {
		store = mock(ScheduleStore.class);
		schedule = mock(MerkleSchedule.class);
		validSig = mock(TransactionSignature.class);
		invalidSig = mock(TransactionSignature.class);
		activationHelper = mock(InHandleActivationHelper.class);

		given(store.get(id)).willReturn(schedule);
		given(schedule.transactionBody()).willReturn(TransactionBody.getDefaultInstance().toByteArray());
		willAnswer(inv -> {
			Consumer<MerkleSchedule> action = inv.getArgument(1);
			action.accept(schedule);
			return null;
		}).given(store).apply(eq(id), any());

		given(validSig.getSignatureStatus()).willReturn(VerificationStatus.VALID);
		given(invalidSig.getSignatureStatus()).willReturn(VerificationStatus.INVALID);

		willAnswer(inv -> {
			BiConsumer<JKey, TransactionSignature> visitor = inv.getArgument(0);
			visitor.accept(goodKey, validSig);
			visitor.accept(goodKey, validSig);
			visitor.accept(badKey, invalidSig);
			return null;
		}).given(activationHelper).visitScheduledCryptoSigs(any());
	}

	@Test
	void respondsToNoAttemptsCorrectly() {
		// when:
		var outcome = SignatoryUtils.witnessInScope(0, id, store, activationHelper);

		// then:
		assertEquals(Pair.of(OK, false), outcome);
	}

	@Test
	void respondsToNoAttemptsButNowActiveCorrectly() {
		given(activationHelper.areScheduledPartiesActive(any(), any())).willReturn(true);

		// when:
		var outcome = SignatoryUtils.witnessInScope(0, id, store, activationHelper);

		// then:
		assertEquals(Pair.of(OK, true), outcome);
		// and:
		verify(activationHelper, never()).visitScheduledCryptoSigs(any());
	}

	@Test
	void respondsToPresumedInvalidCorrectly() {
		// when:
		var outcome = SignatoryUtils.witnessInScope(2, id, store, activationHelper);

		// then:
		assertEquals(Pair.of(SOME_SIGNATURES_WERE_INVALID, false), outcome);
	}

	@Test
	void respondsToRepeatedCorrectlyIfNotActive() {
		given(schedule.witnessValidEd25519Signature(eq(goodKey.getEd25519()))).willReturn(false);

		// when:
		var outcome = SignatoryUtils.witnessInScope(1, id, store, activationHelper);

		// then:
		assertEquals(Pair.of(NO_NEW_VALID_SIGNATURES, false), outcome);
	}

	@Test
	void respondsToRepeatedCorrectlyIfActive() {
		given(schedule.witnessValidEd25519Signature(eq(goodKey.getEd25519()))).willReturn(false);
		given(activationHelper.areScheduledPartiesActive(any(), any())).willReturn(true);

		// when:
		var outcome = SignatoryUtils.witnessInScope(1, id, store, activationHelper);

		// then:
		assertEquals(Pair.of(OK, true), outcome);
	}

	@Test
	@SuppressWarnings("unchecked")
	void respondsWithActivatingVerdictCorrectly() {
		// setup:
		ArgumentCaptor<BiPredicate<JKey, TransactionSignature>> captor = ArgumentCaptor.forClass(BiPredicate.class);

		given(schedule.witnessValidEd25519Signature(eq(goodKey.getEd25519()))).willReturn(true);
		given(activationHelper.areScheduledPartiesActive(any(), captor.capture())).willReturn(true);

		// when:
		var outcome = SignatoryUtils.witnessInScope(1, id, store, activationHelper);

		// then:
		assertEquals(Pair.of(OK, true), outcome);
		// and:
		var tests = captor.getValue();
		// when:
		tests.test(goodKey, validSig);
		// then:
		verify(schedule).hasValidEd25519Signature(eq(goodKey.getEd25519()));
	}
}