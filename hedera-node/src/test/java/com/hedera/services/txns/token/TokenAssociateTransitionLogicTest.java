package com.hedera.services.txns.token;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class TokenAssociateTransitionLogicTest {
	private AccountID account = IdUtils.asAccount("1.2.4");
	private TokenID id = IdUtils.asToken("1.2.3");

	private TokenStore tokenStore;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;

	private TransactionBody tokenAssociateTxn;
	private TokenAssociateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		tokenStore = mock(TokenStore.class);
		accessor = mock(PlatformTxnAccessor.class);

		txnCtx = mock(TransactionContext.class);

		subject = new TokenAssociateTransitionLogic(tokenStore, txnCtx);
	}

	@Test
	public void capturesInvalidAssociate() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.associate(account, List.of(id))).willReturn(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
	}

	@Test
	public void followsHappyPath() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.associate(account, List.of(id))).willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(tokenStore).associate(account, List.of(id));
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenAssociateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.associate(any(), anyList()))
				.willThrow(IllegalArgumentException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	public void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(tokenAssociateTxn));
	}

	@Test
	public void rejectsMissingAccount() {
		givenMissingAccount();

		// expect:
		assertEquals(INVALID_ACCOUNT_ID, subject.syntaxCheck().apply(tokenAssociateTxn));
	}

	@Test
	public void rejectsDuplicateTokens() {
		givenDuplicateTokens();

		// expect:
		assertEquals(TOKEN_ID_REPEATED_IN_TOKEN_LIST, subject.syntaxCheck().apply(tokenAssociateTxn));
	}

	private void givenValidTxnCtx() {
		tokenAssociateTxn = TransactionBody.newBuilder()
				.setTokenAssociate(TokenAssociateTransactionBody.newBuilder()
						.setAccount(account)
						.addTokens(id))
				.build();
		given(accessor.getTxn()).willReturn(tokenAssociateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(tokenStore.resolve(id)).willReturn(id);
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
						.addTokens(id)
						.addTokens(id))
				.build();
	}
}
