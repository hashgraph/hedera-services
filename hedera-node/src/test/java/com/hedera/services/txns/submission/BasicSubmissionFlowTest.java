package com.hedera.services.txns.submission;

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

import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.hedera.services.context.ServicesNodeType.STAKED_NODE;
import static com.hedera.services.context.ServicesNodeType.ZERO_STAKE_NODE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.never;


@ExtendWith(MockitoExtension.class)
class BasicSubmissionFlowTest {
	private final long someReqFee = 1_234L;
	private final TxnValidityAndFeeReq someFailure = new TxnValidityAndFeeReq(INSUFFICIENT_TX_FEE, someReqFee);
	private final TxnValidityAndFeeReq someSuccess = new TxnValidityAndFeeReq(OK, someReqFee);

	private final Transaction someTxn = Transaction.getDefaultInstance();
	private final SignedTxnAccessor someAccessor = SignedTxnAccessor.uncheckedFrom(someTxn);

	@Mock
	private TransactionPrecheck precheck;
	@Mock
	private PlatformSubmissionManager submissionManager;

	private BasicSubmissionFlow subject;

	@Test
	void rejectsAllOnZeroStakeNode() {
		setupZeroStakeNode();

		// given:
		var response = subject.submit(Transaction.getDefaultInstance());

		// then:
		assertEquals(INVALID_NODE_ACCOUNT, response.getNodeTransactionPrecheckCode());
	}

	@Test
	void rejectsPrecheckFailures() {
		setupStakedNode();

		given(precheck.performForTopLevel(someTxn)).willReturn(Pair.of(someFailure, Optional.empty()));

		// when:
		var response = subject.submit(someTxn);

		// then:
		assertEquals(INSUFFICIENT_TX_FEE, response.getNodeTransactionPrecheckCode());
		assertEquals(someReqFee, response.getCost());
	}

	@Test
	void translatesPlatformCreateFailure() {
		setupStakedNode();
		givenValidPrecheck();
		given(submissionManager.trySubmission(any())).willReturn(PLATFORM_TRANSACTION_NOT_CREATED);

		// when:
		var response = subject.submit(someTxn);

		// then:
		assertEquals(PLATFORM_TRANSACTION_NOT_CREATED, response.getNodeTransactionPrecheckCode());
	}

	@Test
	void rejectsInvalidAccessor() {
		setupStakedNode();
		given(precheck.performForTopLevel(someTxn)).willReturn(Pair.of(someSuccess, Optional.empty()));

		// when:
		var response = subject.submit(someTxn);

		// then:
		assertEquals(FAIL_INVALID, response.getNodeTransactionPrecheckCode());
		verify(submissionManager, never()).trySubmission(any());
	}

	@Test
	void followsHappyPathToOk() {
		setupStakedNode();
		givenValidPrecheck();
		givenOkSubmission();

		// when:
		TransactionResponse response = subject.submit(someTxn);

		// then:
		assertEquals(OK, response.getNodeTransactionPrecheckCode());
	}

	private void givenOkSubmission() {
		given(submissionManager.trySubmission(any())).willReturn(OK);
	}

	private void givenValidPrecheck() {
		given(precheck.performForTopLevel(someTxn)).willReturn(Pair.of(someSuccess, Optional.of(someAccessor)));
	}

	private void setupStakedNode() {
		subject = new BasicSubmissionFlow(STAKED_NODE, precheck, submissionManager);
	}

	private void setupZeroStakeNode() {
		subject = new BasicSubmissionFlow(ZERO_STAKE_NODE, precheck, submissionManager);
	}
}
