package com.hedera.services.txns.submission;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

import com.google.protobuf.ByteString;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.legacy.exception.PlatformTransactionCreationException;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.swirlds.common.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class TxnHandlerSubmissionFlowTest {
	private static final byte[] NONSENSE = "Jabberwocky".getBytes();

	long feeRequired = 1_234L;
	TransactionID txnId = TransactionID.newBuilder().setAccountID(asAccount("0.0.2")).build();
	Transaction signedTxn = Transaction.newBuilder()
			.setBody(TransactionBody.newBuilder().setTransactionID(txnId))
			.build();
	TxnValidityAndFeeReq okMeta = new TxnValidityAndFeeReq(OK);

	private Platform platform;
	private TransitionLogic logic;
	private TransactionHandler txnHandler;
	private TransitionLogicLookup logicLookup;
	private Function<TransactionBody, ResponseCodeEnum> syntaxCheck;

	private TxnHandlerSubmissionFlow subject;

	@BeforeEach
	private void setup() {
		logic = mock(TransitionLogic.class);
		platform = mock(Platform.class);
		txnHandler = mock(TransactionHandler.class);
		syntaxCheck = mock(Function.class);
		given(logic.syntaxCheck()).willReturn(syntaxCheck);
		logicLookup = mock(TransitionLogicLookup.class);
		given(logicLookup.lookupFor(signedTxn.getBody())).willReturn(Optional.of(logic));

		subject = new TxnHandlerSubmissionFlow(platform, txnHandler, logicLookup);
	}

	@Test
	public void rejectsNonsenseTransaction() {
		// given:
		Transaction signedNonsenseTxn = Transaction.newBuilder()
				.setBodyBytes(ByteString.copyFrom(NONSENSE))
				.build();

		// when:
		TransactionResponse response = subject.submit(signedNonsenseTxn);

		// then:
		assertEquals(INVALID_TRANSACTION_BODY, response.getNodeTransactionPrecheckCode());
	}

	@Test
	public void shortCircuitsOnEmptyTxn() {
		// when:
		TransactionResponse response = subject.submit(Transaction.getDefaultInstance());

		// then:
		assertEquals(INVALID_TRANSACTION_BODY, response.getNodeTransactionPrecheckCode());
	}

	@Test
	public void shortCircuitsOnInvalidMeta() {
		// setup:
		TxnValidityAndFeeReq metaValidity = new TxnValidityAndFeeReq(INSUFFICIENT_PAYER_BALANCE, feeRequired);

		given(txnHandler.validateTransactionPreConsensus(signedTxn, false)).willReturn(metaValidity);

		// when:
		TransactionResponse response = subject.submit(signedTxn);

		// then:
		assertEquals(INSUFFICIENT_PAYER_BALANCE, response.getNodeTransactionPrecheckCode());
		assertEquals(feeRequired, response.getCost());
	}

	@Test
	public void rejectsInvalidSyntax() {
		given(txnHandler.validateTransactionPreConsensus(signedTxn, false)).willReturn(okMeta);
		given(syntaxCheck.apply(any())).willReturn(INVALID_ACCOUNT_ID);

		// when:
		TransactionResponse response = subject.submit(signedTxn);

		// then:
		assertEquals(INVALID_ACCOUNT_ID, response.getNodeTransactionPrecheckCode());
	}

	@Test
	public void catchesPlatformCreateEx() throws Exception {
		given(txnHandler.validateTransactionPreConsensus(signedTxn, false)).willReturn(okMeta);
		given(syntaxCheck.apply(any())).willReturn(OK);
		given(txnHandler.submitTransaction(platform, signedTxn, txnId)).willReturn(false);

		// when:
		TransactionResponse response = subject.submit(signedTxn);

		// then:
		assertEquals(PLATFORM_TRANSACTION_NOT_CREATED, response.getNodeTransactionPrecheckCode());
	}

	@Test
	public void followsHappyPathToOk() throws Exception {
		given(txnHandler.validateTransactionPreConsensus(signedTxn, false)).willReturn(okMeta);
		given(syntaxCheck.apply(any())).willReturn(OK);
		given(txnHandler.submitTransaction(platform, signedTxn, txnId)).willReturn(true);

		// when:
		TransactionResponse response = subject.submit(signedTxn);

		// then:
		assertEquals(OK, response.getNodeTransactionPrecheckCode());
	}

	@Test
	public void usesFallbackSyntaxCheckIfNotSupported() throws Exception {
		given(txnHandler.validateTransactionPreConsensus(signedTxn, false)).willReturn(okMeta);
		given(logicLookup.lookupFor(any())).willReturn(Optional.empty());

		// when:
		TransactionResponse response = subject.submit(signedTxn);

		// then:
		assertEquals(NOT_SUPPORTED, response.getNodeTransactionPrecheckCode());
	}
}
