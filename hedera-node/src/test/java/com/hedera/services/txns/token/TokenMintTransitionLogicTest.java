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
import com.hedera.services.tokens.TokenStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;

@RunWith(JUnitPlatform.class)
class TokenMintTransitionLogicTest {
	long amount = 123L;
	private TokenID id = IdUtils.asToken("1.2.3");

	private TokenStore tokenStore;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;

	private TransactionBody tokenMintTxn;
	private TokenMintTransitionLogic subject;

	@BeforeEach
	private void setup() {
		tokenStore = mock(TokenStore.class);
		accessor = mock(PlatformTxnAccessor.class);

		txnCtx = mock(TransactionContext.class);

		subject = new TokenMintTransitionLogic(tokenStore, txnCtx);
	}

	@Test
	public void capturesInvalidMint() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.mint(id, amount)).willReturn(INVALID_TOKEN_MINT_AMOUNT);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_TOKEN_MINT_AMOUNT);
	}

	@Test
	public void rejectsBadRefForSafety() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.resolve(id)).willReturn(TokenStore.MISSING_TOKEN);

		// when:
		subject.doStateTransition();

		// then:
		verify(tokenStore, never()).mint(id, amount);
		verify(txnCtx).setStatus(INVALID_TOKEN_ID);
	}

	@Test
	public void followsHappyPath() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.mint(id, amount)).willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(tokenStore).mint(id, amount);
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenMintTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.mint(any(), anyLong()))
				.willThrow(IllegalArgumentException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	private void givenValidTxnCtx() {
		tokenMintTxn = TransactionBody.newBuilder()
				.setTokenMint(TokenMintTransactionBody.newBuilder()
						.setToken(id)
						.setAmount(amount))
				.build();
		given(accessor.getTxn()).willReturn(tokenMintTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(tokenStore.resolve(id)).willReturn(id);
	}
}
