package com.hedera.services.grpc.controllers;

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

import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class FreezeControllerTest {
	Transaction txn = Transaction.getDefaultInstance();
	TxnResponseHelper txnResponseHelper;
	StreamObserver<TransactionResponse> txnObserver;

	FreezeController subject;

	@BeforeEach
	private void setup() {
		txnResponseHelper = mock(TxnResponseHelper.class);

		subject = new FreezeController(txnResponseHelper);
	}

	@Test
	public void forwardsTransferAsExpected() {
		// when:
		subject.freeze(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, Freeze);
	}
}
