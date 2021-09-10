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

import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.txns.SubmissionFlow;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static com.hedera.services.txns.submission.TxnResponseHelper.FAIL_INVALID_RESPONSE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.Mockito.times;

class TxnResponseHelperTest {
	final Transaction txn = Transaction.getDefaultInstance();
	TransactionResponse okResponse;
	TransactionResponse notOkResponse;

	SubmissionFlow submissionFlow;
	HapiOpCounters opCounters;
	StreamObserver<TransactionResponse> observer;
	SignedTxnAccessor accessor;
	TxnResponseHelper subject;

	@BeforeEach
	private void setup() {
		submissionFlow = mock(SubmissionFlow.class);
		opCounters = mock(HapiOpCounters.class);
		observer = mock(StreamObserver.class);
		okResponse = mock(TransactionResponse.class);
		given(okResponse.getNodeTransactionPrecheckCode()).willReturn(OK);
		notOkResponse = mock(TransactionResponse.class);
		accessor = mock(SignedTxnAccessor.class);
		given(accessor.getSignedTxnWrapper()).willReturn(null);

		subject = new TxnResponseHelper(submissionFlow, opCounters);
	}

	@Test
	void helpsWithSubmitHappyPath() {
		InOrder inOrder = inOrder(submissionFlow, opCounters, observer);
		given(submissionFlow.submit(txn)).willReturn(okResponse);

		subject.submit(txn, observer, CryptoTransfer);

		inOrder.verify(opCounters).countReceived(CryptoTransfer);
		inOrder.verify(submissionFlow).submit(txn);
		inOrder.verify(observer).onNext(okResponse);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(opCounters).countSubmitted(CryptoTransfer);
	}

	@Test
	void helpsWithSubmitUnhappyPath() {
		InOrder inOrder = inOrder(submissionFlow, opCounters, observer);
		given(submissionFlow.submit(txn)).willReturn(notOkResponse);

		subject.submit(txn, observer, CryptoTransfer);

		inOrder.verify(opCounters).countReceived(CryptoTransfer);
		inOrder.verify(submissionFlow).submit(txn);
		inOrder.verify(observer).onNext(notOkResponse);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(opCounters, never()).countSubmitted(CryptoTransfer);
	}

	@Test
	void helpsWithExceptionOnSubmit() {
		InOrder inOrder = inOrder(submissionFlow, opCounters, accessor, observer);

		try (MockedStatic<SignedTxnAccessor> accessors = Mockito.mockStatic(SignedTxnAccessor.class)) {
			accessors.when(() -> SignedTxnAccessor.uncheckedFrom(txn))
					.thenReturn(accessor);
			given(submissionFlow.submit(txn)).willThrow(IllegalArgumentException.class);

			subject.submit(txn, observer, CryptoTransfer);

			inOrder.verify(opCounters).countReceived(CryptoTransfer);
			inOrder.verify(submissionFlow).submit(txn);
			inOrder.verify(accessor, times(1)).getSignedTxnWrapper();
			inOrder.verify(observer).onNext(FAIL_INVALID_RESPONSE);
			inOrder.verify(observer).onCompleted();
			inOrder.verify(opCounters, never()).countSubmitted(CryptoTransfer);
		}
	}
}
