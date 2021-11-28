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
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenDissociateTransitionLogicTest {
	private final AccountID targetAccount = IdUtils.asAccount("1.2.3");
	private final TokenID firstTargetToken = IdUtils.asToken("2.3.4");

	@Mock
	private TransactionContext txnCtx;
	@Mock
	private DissociateLogic dissociateLogic;
	@Mock
	private TxnAccessor accessor;

	private TokenDissociateTransitionLogic subject;

	@BeforeEach
	void setUp() {
		subject = new TokenDissociateTransitionLogic(txnCtx, dissociateLogic);
	}

	@Test
	void oksValidTxn() {
		// expect:
		assertEquals(OK, subject.semanticCheck().apply(validDissociateTxn()));
	}

	@Test
	void hasCorrectApplicability() {
		// expect:
		assertTrue(subject.applicability().test(validDissociateTxn()));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void rejectsMissingAccountId() {
		// given:
		final var check = subject.semanticCheck();

		// expect:
		assertEquals(INVALID_ACCOUNT_ID, check.apply(dissociateTxnWith(missingAccountIdOp())));
	}

	@Test
	void rejectsRepatedTokenId() {
		// given:
		final var check = subject.semanticCheck();

		// expect:
		assertEquals(TOKEN_ID_REPEATED_IN_TOKEN_LIST, check.apply(dissociateTxnWith(repeatedTokenIdOp())));
	}

	@Test
	void callsDissociateLogicWithCorrectParams() {
		final var accountId = new Id(1, 2, 3);

		given(accessor.getTxn()).willReturn(validDissociateTxn());
		given(txnCtx.accessor()).willReturn(accessor);

		subject.doStateTransition();

		verify(dissociateLogic).dissociate(accountId, txnCtx.accessor().getTxn().getTokenDissociate().getTokensList());
	}

	private TransactionBody validDissociateTxn() {
		return TransactionBody.newBuilder()
				.setTokenDissociate(validOp())
				.build();
	}

	private TransactionBody dissociateTxnWith(TokenDissociateTransactionBody op) {
		return TransactionBody.newBuilder()
				.setTokenDissociate(op)
				.build();
	}

	private TokenDissociateTransactionBody validOp() {
		return TokenDissociateTransactionBody.newBuilder()
				.setAccount(targetAccount)
				.addTokens(firstTargetToken)
				.build();
	}

	private TokenDissociateTransactionBody missingAccountIdOp() {
		return TokenDissociateTransactionBody.newBuilder()
				.addTokens(firstTargetToken)
				.build();
	}

	private TokenDissociateTransactionBody repeatedTokenIdOp() {
		return TokenDissociateTransactionBody.newBuilder()
				.setAccount(targetAccount)
				.addTokens(firstTargetToken)
				.addTokens(firstTargetToken)
				.build();
	}
}
