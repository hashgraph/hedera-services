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
import com.hederahashgraph.api.proto.java.HederaFunctionality;
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
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenTransact;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
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
		verify(txnResponseHelper).submit(txn, txnObserver, TokenCreate);
	}

	@Test
	public void forwardTokenTransactAsExpected() {
		// when:
		subject.transferTokens(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenTransact);
	}

	@Test
	public void forwardTokenFreezeAsExpected() {
		// when:
		subject.freezeTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenFreezeAccount);
	}

	@Test
	public void forwardTokenUnfreezeAsExpected() {
		// when:
		subject.unfreezeTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenUnfreezeAccount);
	}

	@Test
	public void forwardGrantKyc() {
		// when:
		subject.grantKycToTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenGrantKycToAccount);
	}

	@Test
	public void forwardRevokeKyc() {
		// when:
		subject.revokeKycFromTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenRevokeKycFromAccount);
	}

	@Test
	public void forwardDelete() {
		// when:
		subject.deleteToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenDelete);
	}

	@Test
	public void forwardUpdate() {
		// when:
		subject.updateToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenUpdate);
	}

	@Test
	public void forwardMint() {
		// when:
		subject.mintToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenMint);
	}

	@Test
	public void forwardBurn() {
		// when:
		subject.burnToken(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenBurn);
	}

	@Test
	public void forwardWipe() {
		// when:
		subject.wipeTokenAccount(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenAccountWipe);
	}

	@Test
	public void forwardDissociate() {
		// when:
		subject.dissociateTokens(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenDissociateFromAccount);
	}

	@Test
	public void forwardAssociate() {
		// when:
		subject.associateTokens(txn, txnObserver);

		// expect:
		verify(txnResponseHelper).submit(txn, txnObserver, TokenAssociateToAccount);
	}

	@Test
	public void forwardsTokenInfoAsExpected() {
		// when:
		subject.getTokenInfo(query, queryObserver);

		// expect:
		verify(queryResponseHelper).answer(query, queryObserver,null , TokenGetInfo);
	}
}