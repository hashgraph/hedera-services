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
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

class TokenDissociateTransitionLogicTest {
	private long tokenNum = 12345L;
	private long accountNum = 54321L;
	private long treasuryNum = 77777L;
	private AccountID accountID = IdUtils.asAccount("0.0." + accountNum);
	private TokenID tokenID = IdUtils.asToken("0.0." + tokenNum);
	private Id accountId = new Id(0,0,accountNum);
	private Id tokenId = new Id(0,0,tokenNum);
	private Id treasuryId = new Id(0,0,treasuryNum);

	private AccountStore accountStore;
	private TypedTokenStore tokenStore;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private Account account;
	private Account treasury;
	private Token token;
	private TokenRelationship tokenRelationship;
	private TokenRelationship treasuryRelationship;
	private OptionValidator validator;

	private TransactionBody tokenDissociateTxn;
	private TokenDissociateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		tokenStore = mock(TypedTokenStore.class);
		accountStore = mock(AccountStore.class);
		accessor = mock(PlatformTxnAccessor.class);
		account = mock(Account.class);
		treasury = mock(Account.class);
		token = mock(Token.class);
		tokenRelationship = mock(TokenRelationship.class);
		treasuryRelationship = mock(TokenRelationship.class);
		validator = mock(ContextOptionValidator.class);

		txnCtx = mock(TransactionContext.class);

		subject = new TokenDissociateTransitionLogic(tokenStore, accountStore, txnCtx, validator);
	}

	@Test
	public void capturesInvalidDissociate() {
		givenValidTxnCtx();
		// and:
		doThrow(new InvalidTransactionException(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)).when(tokenStore).loadTokenRelationship(token, account);

		// verify:
		assertFailsWith(() -> subject.doStateTransition(), TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
		verify(tokenStore, never()).loadTokenRelationship(token, treasury);

	}

	@Test
	public void followsHappyPath() {
		givenValidTxnCtx();
		// and:

		// when:
		subject.doStateTransition();

		// then:
		verify(tokenStore).loadPossiblyDeletedToken(tokenId);
		verify(tokenStore).loadTokenRelationship(token, account);
		verify(tokenStore).loadTokenRelationship(token, treasury);
		verify(account).dissociateWith(List.of(Pair.of(tokenRelationship, treasuryRelationship)), validator);
		verify(accountStore).persistAccount(account);
		var inOrder = Mockito.inOrder(tokenStore);
		inOrder.verify(tokenStore).persistTokenRelationships(List.of(treasuryRelationship, tokenRelationship));
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenDissociateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(tokenDissociateTxn));
	}

	@Test
	public void rejectsMissingAccount() {
		givenMissingAccount();

		// expect:
		assertEquals(INVALID_ACCOUNT_ID, subject.semanticCheck().apply(tokenDissociateTxn));
	}

	@Test
	public void rejectsDuplicateTokens() {
		givenDuplicateTokens();

		// expect:
		assertEquals(TOKEN_ID_REPEATED_IN_TOKEN_LIST, subject.semanticCheck().apply(tokenDissociateTxn));
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}

	private void givenValidTxnCtx() {
		tokenDissociateTxn = TransactionBody.newBuilder()
				.setTokenDissociate(TokenDissociateTransactionBody.newBuilder()
						.setAccount(accountID)
						.addTokens(tokenID))
				.build();
		given(accessor.getTxn()).willReturn(tokenDissociateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(token.getTreasury()).willReturn(treasury);
		given(tokenStore.loadPossiblyDeletedToken(tokenId)).willReturn(token);
		given(validator.isValidExpiry(any())).willReturn(true);
		given(accountStore.getValidator()).willReturn(validator);
		given(accountStore.loadAccount(accountId)).willReturn(account);
		given(accountStore.loadAccount(treasuryId)).willReturn(treasury);
		given(tokenStore.loadTokenRelationship(token, account)).willReturn(tokenRelationship);
		given(tokenStore.loadTokenRelationship(token, treasury)).willReturn(treasuryRelationship);
		given(tokenRelationship.getAccount()).willReturn(account);
		given(tokenRelationship.getToken()).willReturn(token);
		given(treasuryRelationship.getAccount()).willReturn(treasury);
		given(treasuryRelationship.getToken()).willReturn(token);
	}

	private void givenMissingAccount() {
		tokenDissociateTxn = TransactionBody.newBuilder()
				.setTokenDissociate(TokenDissociateTransactionBody.newBuilder())
				.build();
	}

	private void givenDuplicateTokens() {
		tokenDissociateTxn = TransactionBody.newBuilder()
				.setTokenDissociate(TokenDissociateTransactionBody.newBuilder()
						.setAccount(accountID)
						.addTokens(tokenID)
						.addTokens(tokenID))
				.build();
	}
}
