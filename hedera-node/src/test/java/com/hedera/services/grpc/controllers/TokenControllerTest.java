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

import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.meta.MetaAnswers;
import com.hedera.services.queries.token.TokenAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.grpc.controllers.NetworkController.GET_VERSION_INFO_METRIC;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.grpc.controllers.TokenController.*;

@RunWith(JUnitPlatform.class)
class TokenControllerTest {
	Query query = Query.getDefaultInstance();
	Transaction txn = Transaction.getDefaultInstance();

	TokenAnswers answers;
	TxnResponseHelper txnResponseHelper;
	QueryResponseHelper queryResponseHelper;
	StreamObserver<Response> queryObserver;
	StreamObserver<TransactionResponse> txnObserver;

	TokenController subject;

	@BeforeEach
	private void setup() {
		answers = mock(TokenAnswers.class);
		txnObserver = mock(StreamObserver.class);
		queryObserver = mock(StreamObserver.class);

		txnResponseHelper = mock(TxnResponseHelper.class);
		queryResponseHelper = mock(QueryResponseHelper.class);

		subject = new TokenController(answers, txnResponseHelper, queryResponseHelper);
	}

	@Test
	public void forwardTokenCreateAsExpected() {
		// when:
		subject.createToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToToken(txn, txnObserver, TOKEN_CREATE_METRIC);
	}

	@Test
	public void forwardTokenTransactAsExpected() {
		// when:
		subject.transferTokens(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToToken(txn, txnObserver, TOKEN_TRANSACT_METRIC);
	}

	@Test
	public void forwardTokenFreezeAsExpected() {
		// when:
		subject.freezeTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToToken(txn, txnObserver, TOKEN_FREEZE_METRIC);
	}

	@Test
	public void forwardTokenUnfreezeAsExpected() {
		// when:
		subject.unfreezeTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToToken(txn, txnObserver, TOKEN_UNFREEZE_METRIC);
	}

	@Test
	public void forwardGrantKyc() {
		// when:
		subject.grantKycToTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToToken(txn, txnObserver, TOKEN_GRANT_KYC_METRIC);
	}

	@Test
	public void forwardRevokeKyc() {
		// when:
		subject.revokeKycFromTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToToken(txn, txnObserver, TOKEN_REVOKE_KYC_METRIC);
	}

	@Test
	public void forwardDelete() {
		// when:
		subject.deleteToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToToken(txn, txnObserver, TOKEN_DELETE_METRIC);
	}

	@Test
	public void forwardUpdate() {
		// when:
		subject.updateToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToToken(txn, txnObserver, TOKEN_UPDATE_METRIC);
	}

	@Test
	public void forwardMint() {
		// when:
		subject.mintToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToToken(txn, txnObserver, TOKEN_MINT_METRIC);
	}

	@Test
	public void forwardBurn() {
		// when:
		subject.burnToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToToken(txn, txnObserver, TOKEN_BURN_METRIC);
	}

	@Test
	public void forwardWipe() {
		// when:
		subject.wipeTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).respondToToken(txn, txnObserver, TOKEN_WIPE_ACCOUNT_METRIC);
	}

	@Test
	public void forwardsTokenInfoAsExpected() {
		// when:
		subject.getTokenInfo(query, queryObserver);

		// expect:
		verify(queryResponseHelper).respondToToken(query, queryObserver, null, TOKEN_GET_INFO_METRIC);
	}
}