package com.hedera.services.txns.token;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TokenAssociateTransitionLogicTest {
	private final AccountID account = IdUtils.asAccount("0.0.2");
	private final TokenID firstToken = IdUtils.asToken("1.2.3");
	private final TokenID secondToken = IdUtils.asToken("2.3.4");
	private TransactionBody tokenAssociateTxn;
	private TokenAssociateTransitionLogic subject;

	@Mock private TransactionContext txnCtx;
	@Mock private AssociateLogic associateLogic;

	@BeforeEach
	private void setup() {
		subject = new TokenAssociateTransitionLogic(txnCtx, associateLogic);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenAssociateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(tokenAssociateTxn));
	}

	@Test
	void rejectsMissingAccount() {
		givenMissingAccount();

		// expect:
		assertEquals(INVALID_ACCOUNT_ID, subject.semanticCheck().apply(tokenAssociateTxn));
	}

	@Test
	void rejectsDuplicateTokens() {
		givenDuplicateTokens();

		// expect:
		assertEquals(TOKEN_ID_REPEATED_IN_TOKEN_LIST, subject.semanticCheck().apply(tokenAssociateTxn));
	}

	private void givenValidTxnCtx() {
		tokenAssociateTxn = TransactionBody.newBuilder()
				.setTokenAssociate(TokenAssociateTransactionBody.newBuilder()
						.setAccount(account)
						.addAllTokens(List.of(firstToken, secondToken)))
				.build();
	}

	private void givenMissingAccount() {
		tokenAssociateTxn = TransactionBody.newBuilder()
				.setTokenAssociate(TokenAssociateTransactionBody.newBuilder())
				.build();
	}

	private void givenDuplicateTokens() {
		tokenAssociateTxn = TransactionBody.newBuilder()
				.setTokenAssociate(TokenAssociateTransactionBody.newBuilder()
						.setAccount(account)
						.addTokens(firstToken)
						.addTokens(firstToken))
				.build();
	}
}
