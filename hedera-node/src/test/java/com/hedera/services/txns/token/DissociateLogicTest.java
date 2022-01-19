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
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.factories.keys.KeyFactory;
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

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DissociateLogicTest {
	private final AccountID targetAccount = IdUtils.asAccount("1.2.3");
	private final TokenID firstTargetToken = IdUtils.asToken("2.3.4");
	private final Id accountId = new Id(1, 2, 3);
	private final Id tokenId = new Id(2, 3, 4);
	private final AccountID accountID = accountId.asGrpcAccount();

	@Mock
	private TxnAccessor accessor;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private AccountStore accountStore;
	@Mock
	private Account account;
	@Mock
	private DissociationFactory relsFactory;
	@Mock
	private TypedTokenStore tokenStore;
	@Mock
	private Dissociation dissociation;
	@Mock
	private TokenRelationship tokenRelationship;
	@Mock
	private OptionValidator validator;

	private DissociateLogic subject;

	private final ByteString alias = KeyFactory.getDefaultInstance().newEd25519().getEd25519();
	private final AccountID accountWithAlias = AccountID.newBuilder().setAlias(alias).build();
	private final long mappedAliasNum = 1234L;
	private final Id mappedAliasId = new Id(0, 0, mappedAliasNum);

	@BeforeEach
	private void setup() {
		subject = new DissociateLogic(validator, tokenStore, accountStore, relsFactory);
	}

	@Test
	void performsExpectedLogic() {
		given(accessor.getTxn()).willReturn(validDissociateTxn());
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.getAccountNumFromAlias(accountID.getAlias(), accountID.getAccountNum()))
				.willReturn(accountID.getAccountNum());
		given(accountStore.loadAccount(accountId)).willReturn(account);
		// and:
		given(relsFactory.loadFrom(tokenStore, account, tokenId)).willReturn(dissociation);
		willAnswer(invocationOnMock -> {
			((List<TokenRelationship>) invocationOnMock.getArgument(0)).add(tokenRelationship);
			return null;
		}).given(dissociation).addUpdatedModelRelsTo(anyList());

		// when:
		subject.dissociate(accountId.asGrpcAccount(), txnCtx.accessor().getTxn().getTokenDissociate().getTokensList());

		// then:
		verify(account).dissociateUsing(List.of(dissociation), validator);
		// and:
		verify(accountStore).commitAccount(account);
		verify(tokenStore).commitTokenRelationships(List.of(tokenRelationship));
	}

	@Test
	void performsExpectedLogicWithAlias() {
		given(accessor.getTxn()).willReturn(validDissociateTxnWithAlias());
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.getAccountNumFromAlias(accountWithAlias.getAlias(), accountWithAlias.getAccountNum()))
				.willReturn(mappedAliasNum);
		given(accountStore.loadAccount(mappedAliasId)).willReturn(account);
		// and:
		given(relsFactory.loadFrom(tokenStore, account, tokenId)).willReturn(dissociation);
		willAnswer(invocationOnMock -> {
			((List<TokenRelationship>) invocationOnMock.getArgument(0)).add(tokenRelationship);
			return null;
		}).given(dissociation).addUpdatedModelRelsTo(anyList());

		subject.dissociate(accountWithAlias, txnCtx.accessor().getTxn().getTokenDissociate().getTokensList());

		verify(account).dissociateUsing(List.of(dissociation), validator);
	}

	private TransactionBody validDissociateTxnWithAlias() {
		return TransactionBody.newBuilder()
				.setTokenDissociate(TokenDissociateTransactionBody.newBuilder()
						.setAccount(accountWithAlias)
						.addTokens(firstTargetToken))
				.build();
	}

	@Test
	void failsAsExpectedWithInvalidAlias() {
		given(accessor.getTxn()).willReturn(validDissociateTxnWithAlias());
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.getAccountNumFromAlias(accountWithAlias.getAlias(), accountWithAlias.getAccountNum()))
				.willThrow(new InvalidTransactionException(INVALID_ACCOUNT_ID));

		final var tokenList = txnCtx.accessor().getTxn().getTokenDissociate().getTokensList();

		final var ex = assertThrows(InvalidTransactionException.class,
				() -> subject.dissociate(accountWithAlias, tokenList));
		assertEquals(INVALID_ACCOUNT_ID, ex.getResponseCode());
	}


	private TransactionBody validDissociateTxn() {
		return TransactionBody.newBuilder()
				.setTokenDissociate(validOp())
				.build();
	}

	private TokenDissociateTransactionBody validOp() {
		return TokenDissociateTransactionBody.newBuilder()
				.setAccount(targetAccount)
				.addTokens(firstTargetToken)
				.build();
	}
}
