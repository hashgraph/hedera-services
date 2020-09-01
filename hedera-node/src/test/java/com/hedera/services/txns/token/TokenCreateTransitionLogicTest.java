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
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.tokens.TokenCreationResult;
import com.hedera.services.tokens.TokenStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.math.BigInteger;

import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@RunWith(JUnitPlatform.class)
class TokenCreateTransitionLogicTest {
	private int divisibility = 2;
	private long initialFloat = 1_000_000L;
	private long tinyFloat = BigInteger.valueOf(initialFloat)
			.multiply(BigInteger.valueOf(10).pow(divisibility))
			.longValueExact();
	private AccountID payer = IdUtils.asAccount("1.2.3");
	private AccountID treasury = IdUtils.asAccount("1.2.4");
	private TokenID created = IdUtils.asToken("1.2.666");
	private TransactionBody tokenCreateTxn;

	private TokenStore store;
	private HederaLedger ledger;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;

	private TokenCreateTransitionLogic subject;

	@BeforeEach
	private void setup() {

		store = mock(TokenStore.class);
		ledger = mock(HederaLedger.class);
		accessor = mock(PlatformTxnAccessor.class);

		txnCtx = mock(TransactionContext.class);
		given(txnCtx.activePayer()).willReturn(payer);

		subject = new TokenCreateTransitionLogic(store, ledger, txnCtx);
	}

	@Test
	public void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx();
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer))
				.willThrow(IllegalStateException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx, never()).setCreated(created);
		verify(txnCtx).setStatus(FAIL_INVALID);
		// and:
		verify(store, never()).commitCreation();
		verify(store).rollbackCreation();
		verify(ledger).dropPendingTokenChanges();
	}

	@Test
	public void abortsIfCreationFails() {
		givenValidTxnCtx();
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer))
				.willReturn(TokenCreationResult.failure(INVALID_ADMIN_KEY));

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx, never()).setCreated(created);
		verify(txnCtx).setStatus(INVALID_ADMIN_KEY);
		// and:
		verify(store, never()).commitCreation();
		verify(store).rollbackCreation();
		verify(ledger).dropPendingTokenChanges();
	}

	@Test
	public void abortsIfFloatAdjustFails() {
		givenValidTxnCtx();
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer))
				.willReturn(TokenCreationResult.success(created));
		given(ledger.unfreeze(treasury, created)).willReturn(OK);
		given(ledger.adjustTokenBalance(treasury, created, tinyFloat))
				.willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx, never()).setCreated(created);
		verify(txnCtx).setStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);
		// and:
		verify(store, never()).commitCreation();
		verify(store).rollbackCreation();
		verify(ledger).dropPendingTokenChanges();
	}

	@Test
	public void abortsIfUnfreezeFails() {
		givenValidTxnCtx();
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer))
				.willReturn(TokenCreationResult.success(created));
		given(ledger.unfreeze(treasury, created)).willReturn(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx, never()).setCreated(created);
		verify(txnCtx).setStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);
		// and:
		verify(store, never()).commitCreation();
		verify(store).rollbackCreation();
		verify(ledger).dropPendingTokenChanges();
	}

	@Test
	public void followsHappyPath() {
		givenValidTxnCtx();
		// and:
		given(store.createProvisionally(tokenCreateTxn.getTokenCreation(), payer))
				.willReturn(TokenCreationResult.success(created));
		given(ledger.unfreeze(treasury, created)).willReturn(OK);
		given(ledger.adjustTokenBalance(treasury, created, tinyFloat))
				.willReturn(OK);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setCreated(created);
		verify(txnCtx).setStatus(SUCCESS);
		// and:
		verify(store).commitCreation();
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenCreateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	private void givenValidTxnCtx() {
		tokenCreateTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreation.newBuilder()
						.setFloat(initialFloat)
						.setDivisibility(divisibility)
						.setTreasury(treasury))
				.build();
		given(accessor.getTxn()).willReturn(tokenCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(store.isCreationPending()).willReturn(true);
	}
}