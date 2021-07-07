package com.hedera.services.grpc.controllers;

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

import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.token.TokenAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetAccountNftInfos;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfos;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

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
	 void forwardTokenCreateAsExpected() {
		// when:
		subject.createToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenCreate);
	}

	@Test
	 void forwardTokenFreezeAsExpected() {
		// when:
		subject.freezeTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenFreezeAccount);
	}

	@Test
	 void forwardTokenUnfreezeAsExpected() {
		// when:
		subject.unfreezeTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenUnfreezeAccount);
	}

	@Test
	 void forwardGrantKyc() {
		// when:
		subject.grantKycToTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenGrantKycToAccount);
	}

	@Test
	 void forwardRevokeKyc() {
		// when:
		subject.revokeKycFromTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenRevokeKycFromAccount);
	}

	@Test
	 void forwardDelete() {
		// when:
		subject.deleteToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenDelete);
	}

	@Test
	 void forwardUpdate() {
		// when:
		subject.updateToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenUpdate);
	}

	@Test
	 void forwardMint() {
		// when:
		subject.mintToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenMint);
	}

	@Test
	 void forwardBurn() {
		// when:
		subject.burnToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenBurn);
	}

	@Test
	 void forwardWipe() {
		// when:
		subject.wipeTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenAccountWipe);
	}

	@Test
	 void forwardDissociate() {
		// when:
		subject.dissociateTokens(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenDissociateFromAccount);
	}

	@Test
	 void forwardAssociate() {
		// when:
		subject.associateTokens(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenAssociateToAccount);
	}

	@Test
	 void forwardsTokenInfoAsExpected() {
		// when:
		subject.getTokenInfo(query, queryObserver);

		// expect:
		verify(queryResponseHelper).answer(query, queryObserver, null, TokenGetInfo);
	}

	@Test
	 void forwardsTokenNftInfoAsExpected() {
		// when:
		subject.getTokenNftInfo(query, queryObserver);

		// expect:
		verify(queryResponseHelper).answer(query, queryObserver,null , TokenGetNftInfo);
	}

	@Test
	void forwardsTokenNftInfosAsExpected() {
		// when:
		subject.getTokenNftInfos(query, queryObserver);

		// expect:
		verify(queryResponseHelper).answer(query, queryObserver, null, TokenGetNftInfos);
	}

	@Test
	 void forwardsAccountNftInfosAsExpected() {
		// when:
		subject.getAccountNftInfos(query, queryObserver);

		// expect:
		verify(queryResponseHelper).answer(query, queryObserver,null , TokenGetAccountNftInfos);
	}
}
