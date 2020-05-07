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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.txns.SubmissionFlow;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.legacy.services.stats.HederaNodeStats;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class TxnResponseHelperTest {
	Transaction txn = Transaction.getDefaultInstance();
	String metric = "imaginary";
	TransactionResponse okResponse;
	TransactionResponse notOkResponse;

	SubmissionFlow submissionFlow;
	HederaNodeStats stats;
	StreamObserver<TransactionResponse> observer;
	TxnResponseHelper subject;

	@BeforeEach
	private void setup() {
		submissionFlow = mock(SubmissionFlow.class);
		stats = mock(HederaNodeStats.class);
		observer = mock(StreamObserver.class);
		okResponse = mock(TransactionResponse.class);
		given(okResponse.getNodeTransactionPrecheckCode()).willReturn(OK);
		notOkResponse = mock(TransactionResponse.class);

		subject = new TxnResponseHelper(submissionFlow, stats);
	}

	@Test
	public void helpsWithHcsHappyPath() {
		// setup:
		InOrder inOrder = inOrder(submissionFlow, stats, observer);

		given(submissionFlow.submit(txn)).willReturn(okResponse);

		// when:
		subject.respondToHcs(txn, observer, metric);

		// then:
		inOrder.verify(stats).hcsTxnReceived(metric);
		inOrder.verify(submissionFlow).submit(txn);
		inOrder.verify(observer).onNext(okResponse);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(stats).hcsTxnSubmitted(metric);
	}

	@Test
	public void helpsWithCryptoHappyPath() {
		// setup:
		InOrder inOrder = inOrder(submissionFlow, stats, observer);

		given(submissionFlow.submit(txn)).willReturn(okResponse);

		// when:
		subject.respondToCrypto(txn, observer, metric);

		// then:
		inOrder.verify(stats).cryptoTransactionReceived(metric);
		inOrder.verify(submissionFlow).submit(txn);
		inOrder.verify(observer).onNext(okResponse);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(stats).cryptoTransactionSubmitted(metric);
	}

	@Test
	public void helpsWithFileHappyPath() {
		// setup:
		InOrder inOrder = inOrder(submissionFlow, stats, observer);

		given(submissionFlow.submit(txn)).willReturn(okResponse);

		// when:
		subject.respondToFile(txn, observer, metric);

		// then:
		inOrder.verify(stats).fileTransactionReceived(metric);
		inOrder.verify(submissionFlow).submit(txn);
		inOrder.verify(observer).onNext(okResponse);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(stats).fileTransactionSubmitted(metric);
	}

	@Test
	public void helpsWithUnhappyPath() {
		// setup:
		InOrder inOrder = inOrder(submissionFlow, stats, observer);

		given(submissionFlow.submit(txn)).willReturn(notOkResponse);

		// when:
		subject.respondToCrypto(txn, observer, metric);

		// then:
		inOrder.verify(stats).cryptoTransactionReceived(metric);
		inOrder.verify(submissionFlow).submit(txn);
		inOrder.verify(observer).onNext(notOkResponse);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(stats, never()).cryptoTransactionSubmitted(metric);
	}

	@Test
	public void helpsWithExceptionalPath() {
		// setup:
		InOrder inOrder = inOrder(submissionFlow, stats, observer);

		given(submissionFlow.submit(txn)).willThrow(IllegalStateException.class);

		// when:
		subject.respondToCrypto(txn, observer, metric);

		// then:
		inOrder.verify(stats).cryptoTransactionReceived(metric);
		inOrder.verify(submissionFlow).submit(txn);
		inOrder.verify(observer).onNext(TxnResponseHelper.FAIL_INVALID_RESPONSE);
		inOrder.verify(observer).onCompleted();
		inOrder.verify(stats, never()).cryptoQuerySubmitted(metric);
	}
}
