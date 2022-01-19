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

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

class TokenGrantKycTransitionLogicTest {
	private final ByteString alias = KeyFactory.getDefaultInstance().newEd25519().getEd25519();
	private final long tokenNum = 12345L;
	private final long accountNum = 54321L;
	private final TokenID tokenID = IdUtils.asToken("0.0." + tokenNum);
	private final AccountID accountID = IdUtils.asAccount("0.0." + accountNum);
	private final AccountID accountWithAlias = AccountID.newBuilder().setAlias(alias).build();
	private final long mappedAliasNum = 1234L;
	private final Id mappedAliasId = new Id(0, 0, mappedAliasNum);
	private final Id tokenId = new Id(0,0,tokenNum);
	private final Id accountId = new Id(0,0,accountNum);

	private TypedTokenStore tokenStore;
	private AccountStore accountStore;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private TokenRelationship tokenRelationship;
	private Token token;
	private Account account;

	private TransactionBody tokenGrantKycTxn;
	private TokenGrantKycTransitionLogic subject;

	@BeforeEach
	private void setup() {
		accountStore = mock(AccountStore.class);
		tokenStore = mock(TypedTokenStore.class);
		accessor = mock(PlatformTxnAccessor.class);
		tokenRelationship = mock(TokenRelationship.class);
		token = mock(Token.class);
		account = mock(Account.class);

		txnCtx = mock(TransactionContext.class);

		subject = new TokenGrantKycTransitionLogic(txnCtx, tokenStore, accountStore);
	}

	@Test
	void capturesInvalidGrantKyc() {
		givenValidTxnCtx();
		// and:
		doThrow(new InvalidTransactionException(TOKEN_HAS_NO_KYC_KEY))
				.when(tokenRelationship).changeKycState(true);

		// then:
		assertFailsWith(() -> subject.doStateTransition(), TOKEN_HAS_NO_KYC_KEY);
		verify(tokenStore, never()).commitTokenRelationships(List.of(tokenRelationship));
	}

	@Test
	void followsHappyPath() {
		givenValidTxnCtx();
		// and:
		given(token.hasKycKey()).willReturn(true);

		// when:
		subject.doStateTransition();

		// then:
		verify(tokenRelationship).changeKycState(true);
		verify(tokenStore).commitTokenRelationships(List.of(tokenRelationship));
	}

	@Test
	void followsAliasHappyPath() {
		givenValidAliasTxnCtx();
		given(token.hasKycKey()).willReturn(true);

		subject.doStateTransition();

		verify(tokenRelationship).changeKycState(true);
		verify(tokenStore).commitTokenRelationships(List.of(tokenRelationship));
	}

	@Test
	void failsWhenInvalidAlias() {
		givenValidAliasTxnCtx();
		given(accountStore.getAccountNumFromAlias(accountWithAlias.getAlias(), accountWithAlias.getAccountNum()))
				.willThrow(new InvalidTransactionException(INVALID_ALIAS_KEY));

		final var ex = assertThrows(InvalidTransactionException.class, () -> subject.doStateTransition());
		assertEquals(INVALID_ALIAS_KEY, ex.getResponseCode());
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenGrantKycTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(tokenGrantKycTxn));
	}

	@Test
	void rejectsMissingToken() {
		givenMissingToken();

		// expect:
		assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenGrantKycTxn));
	}

	@Test
	void rejectsMissingAccount() {
		givenMissingAccount();

		// expect:
		assertEquals(INVALID_ACCOUNT_ID, subject.semanticCheck().apply(tokenGrantKycTxn));
	}

	private void givenValidTxnCtx() {
		tokenGrantKycTxn = TransactionBody.newBuilder()
				.setTokenGrantKyc(TokenGrantKycTransactionBody.newBuilder()
						.setAccount(accountID)
						.setToken(tokenID))
				.build();
		given(accessor.getTxn()).willReturn(tokenGrantKycTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.getAccountNumFromAlias(accountID.getAlias(), accountID.getAccountNum()))
				.willReturn(accountNum);
		given(tokenStore.loadToken(tokenId)).willReturn(token);
		given(accountStore.loadAccount(accountId)).willReturn(account);
		given(tokenStore.loadTokenRelationship(token, account)).willReturn(tokenRelationship);
	}

	private void givenValidAliasTxnCtx() {
		tokenGrantKycTxn = TransactionBody.newBuilder()
				.setTokenGrantKyc(TokenGrantKycTransactionBody.newBuilder()
						.setAccount(accountWithAlias)
						.setToken(tokenID))
				.build();
		given(accessor.getTxn()).willReturn(tokenGrantKycTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.getAccountNumFromAlias(accountWithAlias.getAlias(), accountWithAlias.getAccountNum()))
				.willReturn(mappedAliasNum);
		given(tokenStore.loadToken(tokenId)).willReturn(token);
		given(accountStore.loadAccount(mappedAliasId)).willReturn(account);
		given(tokenStore.loadTokenRelationship(token, account)).willReturn(tokenRelationship);
	}

	private void givenMissingToken() {
		tokenGrantKycTxn = TransactionBody.newBuilder()
				.setTokenGrantKyc(TokenGrantKycTransactionBody.newBuilder())
				.build();
	}

	private void givenMissingAccount() {
		tokenGrantKycTxn = TransactionBody.newBuilder()
				.setTokenGrantKyc(TokenGrantKycTransactionBody.newBuilder()
						.setToken(tokenID))
				.build();
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}
