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
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_REF;
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
class TokenBurnTransitionLogicTest {
	long amount = 123L;
	private TokenID id = IdUtils.asToken("1.2.3");
	private TokenRef token = IdUtils.asIdRef("0.0.12345");

	private TokenStore tokenStore;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;

	private TransactionBody tokenBurnTxn;
	private TokenBurnTransitionLogic subject;

	@BeforeEach
	private void setup() {
		tokenStore = mock(TokenStore.class);
		accessor = mock(PlatformTxnAccessor.class);

		txnCtx = mock(TransactionContext.class);

		subject = new TokenBurnTransitionLogic(tokenStore, txnCtx);
	}

	@Test
	public void capturesInvalidBurn() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.burn(id, amount)).willReturn(INVALID_TOKEN_BURN_AMOUNT);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_TOKEN_BURN_AMOUNT);
	}

	@Test
	public void rejectsBadRefForSafety() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.resolve(token)).willReturn(TokenStore.MISSING_TOKEN);

		// when:
		subject.doStateTransition();

		// then:
		verify(tokenStore, never()).burn(id, amount);
		verify(txnCtx).setStatus(INVALID_TOKEN_REF);
	}

	@Test
	public void followsHappyPath() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.burn(id, amount)).willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(tokenStore).burn(id, amount);
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenBurnTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.burn(any(), anyLong()))
				.willThrow(IllegalArgumentException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	private void givenValidTxnCtx() {
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(TokenBurnTransactionBody.newBuilder()
						.setToken(token)
						.setAmount(amount))
				.build();
		given(accessor.getTxn()).willReturn(tokenBurnTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(tokenStore.resolve(token)).willReturn(id);
	}
}
