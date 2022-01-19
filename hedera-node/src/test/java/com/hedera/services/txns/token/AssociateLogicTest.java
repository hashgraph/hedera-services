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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssociateLogicTest {
	private final TokenID firstToken = IdUtils.asToken("1.2.3");
	private final TokenID secondToken = IdUtils.asToken("2.3.4");
	private final Id accountId = new Id(0, 0, 2);
	private final Id firstTokenId = new Id(1, 2, 3);
	private final Id secondTokenId = new Id(2, 3, 4);
	private final AccountID accountID = accountId.asGrpcAccount();

	private final ByteString alias = KeyFactory.getDefaultInstance().newEd25519().getEd25519();
	private final AccountID accountWithAlias = AccountID.newBuilder().setAlias(alias).build();
	private final long mappedAliasNum = 1234L;
	private final Id mappedAliasId = new Id(0, 0, mappedAliasNum);
	private TransactionBody tokenAssociateTxn;

	@Mock private AccountStore accountStore;
	@Mock private TypedTokenStore tokenStore;
	@Mock private Account modelAccount;
	@Mock private Token firstModelToken;
	@Mock private Token secondModelToken;
	@Mock private TokenRelationship firstModelTokenRel;
	@Mock private TokenRelationship secondModelTokenRel;
	@Mock private GlobalDynamicProperties dynamicProperties;

	private AssociateLogic subject;

	@BeforeEach
	private void setup() {
		subject = new AssociateLogic(tokenStore, accountStore, dynamicProperties);
	}

	@Test
	void appliesExpectedTransition() {
		final List<TokenID> tokenIds = List.of(firstToken, secondToken);
		final List<Token> tokens = List.of(firstModelToken, secondModelToken);

		given(accountStore.getAccountNumFromAlias(accountID.getAlias(), accountID.getAccountNum()))
				.willReturn(accountID.getAccountNum());
		given(accountStore.loadAccount(accountId)).willReturn(modelAccount);
		given(tokenStore.loadToken(firstTokenId)).willReturn(firstModelToken);
		given(tokenStore.loadToken(secondTokenId)).willReturn(secondModelToken);
		given(firstModelToken.newRelationshipWith(modelAccount, false)).willReturn(firstModelTokenRel);
		given(secondModelToken.newRelationshipWith(modelAccount, false)).willReturn(secondModelTokenRel);

		subject.associate(accountId.asGrpcAccount(), tokenIds);

		verify(modelAccount).associateWith(tokens, dynamicProperties.maxTokensPerAccount(), false);
		verify(accountStore).commitAccount(modelAccount);
		verify(tokenStore).commitTokenRelationships(List.of(firstModelTokenRel));
		verify(tokenStore).commitTokenRelationships(List.of(secondModelTokenRel));
	}

	@Test
	void appliesExpectedTransitionWithAlias() {
		final List<TokenID> tokenIds = List.of(firstToken, secondToken);
		final List<Token> tokens = List.of(firstModelToken, secondModelToken);

		given(accountStore.getAccountNumFromAlias(alias, accountWithAlias.getAccountNum())).willReturn(mappedAliasNum);
		given(accountStore.loadAccount(mappedAliasId)).willReturn(modelAccount);
		given(tokenStore.loadToken(firstTokenId)).willReturn(firstModelToken);
		given(tokenStore.loadToken(secondTokenId)).willReturn(secondModelToken);
		given(firstModelToken.newRelationshipWith(modelAccount, false)).willReturn(firstModelTokenRel);
		given(secondModelToken.newRelationshipWith(modelAccount, false)).willReturn(secondModelTokenRel);
		given(dynamicProperties.maxTokensPerAccount()).willReturn(123);

		subject.associate(accountWithAlias, tokenIds);

		verify(modelAccount).associateWith(tokens, dynamicProperties.maxTokensPerAccount(), false);
		verify(accountStore).commitAccount(modelAccount);
		verify(tokenStore).commitTokenRelationships(List.of(firstModelTokenRel));
		verify(tokenStore).commitTokenRelationships(List.of(secondModelTokenRel));
	}

	@Test
	void failsAsExpectedTransitionWithInvalidAlias() {
		final List<TokenID> tokenIds = List.of(firstToken, secondToken);
		final List<Token> tokens = List.of(firstModelToken, secondModelToken);

		given(accountStore.getAccountNumFromAlias(alias, accountWithAlias.getAccountNum()))
				.willThrow(new InvalidTransactionException(INVALID_ACCOUNT_ID));

		final var ex = assertThrows(InvalidTransactionException.class, () -> subject.associate(accountWithAlias, tokenIds));
		assertEquals(INVALID_ACCOUNT_ID, ex.getResponseCode());
	}
}