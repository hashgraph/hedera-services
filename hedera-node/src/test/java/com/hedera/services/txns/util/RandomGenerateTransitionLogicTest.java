package com.hedera.services.txns.util;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.RandomGenerateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.context.SideEffectsTracker.MAX_PSEUDORANDOM_LENGTH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RANDOM_GENERATE_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RandomGenerateTransitionLogicTest {
	private static final AccountID PAYER = AccountID.newBuilder().setAccountNum(1_234L).build();
	private static final Hash aFullHash = new Hash(TxnUtils.randomUtf8Bytes(MAX_PSEUDORANDOM_LENGTH));

	@Mock
	private SideEffectsTracker tracker;
	@Mock
	private RecordsRunningHashLeaf runningHashLeaf;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private SignedTxnAccessor accessor;

	private RandomGenerateTransitionLogic subject;
	private TransactionBody randomGenerateTxn;

	@BeforeEach
	private void setup() {
		subject = new RandomGenerateTransitionLogic(txnCtx, tracker, () -> runningHashLeaf);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx(0);

		assertTrue(subject.applicability().test(randomGenerateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void rejectsInvalidRange() {
		givenValidTxnCtx(-10000);
		assertEquals(INVALID_RANDOM_GENERATE_RANGE, subject.semanticCheck().apply(randomGenerateTxn));
	}

	@Test
	void acceptsPositiveAndZeroRange() {
		givenValidTxnCtx(10000);
		assertEquals(OK, subject.semanticCheck().apply(randomGenerateTxn));

		givenValidTxnCtx(0);
		assertEquals(OK, subject.semanticCheck().apply(randomGenerateTxn));
	}

	@Test
	void acceptsNoRange() {
		givenValidTxnCtxWithoutRange();
		assertEquals(OK, subject.semanticCheck().apply(randomGenerateTxn));
	}

	@Test
	void followsHappyPath() throws InterruptedException {
		givenValidTxnCtxWithoutRange();
		given(runningHashLeaf.currentRunningHash()).willReturn(aFullHash);
		given(accessor.getTxn()).willReturn(randomGenerateTxn);
		given(txnCtx.activePayer()).willReturn(ourAccount());
		given(txnCtx.accessor()).willReturn(accessor);

		subject.doStateTransition();
		verify(tracker).trackPseudoRandomBytes(aFullHash.getValue());
		verify(tracker, never()).trackPseudoRandomNumber(anyInt());
		verify(txnCtx).setStatus(SUCCESS);
	}

	private void givenValidTxnCtx(int range) {
		final var opBuilder = RandomGenerateTransactionBody.newBuilder()
				.setRange(range);
		randomGenerateTxn = TransactionBody.newBuilder().setRandomGenerate(opBuilder).build();
	}

	private void givenValidTxnCtxWithoutRange() {
		final var opBuilder = RandomGenerateTransactionBody.newBuilder();
		randomGenerateTxn = TransactionBody.newBuilder().setRandomGenerate(opBuilder).build();
	}

	private AccountID ourAccount() {
		return PAYER;
	}
}
