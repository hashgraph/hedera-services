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
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenBurnTransitionLogicTest {
	private final long amount = 123L;
	private final TokenID grpcId = IdUtils.asToken("1.2.3");
	private final Id id = new Id(1, 2, 3);
	private final Id treasuryId = new Id(2, 4, 6);
	private final Account treasury = new Account(treasuryId);

	@Mock
	private Token token;
	@Mock
	private TypedTokenStore store;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private OptionValidator validator;
	@Mock
	private AccountStore accountStore;

	private TokenRelationship treasuryRel;
	private TransactionBody tokenBurnTxn;

	private TokenBurnTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new TokenBurnTransitionLogic(validator, accountStore, store, txnCtx);
	}

	@Test
	void followsHappyPathForCommon() {
		// setup:
		treasuryRel = new TokenRelationship(token, treasury);

		givenValidTxnCtx();
		given(accessor.getTxn()).willReturn(tokenBurnTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(store.loadToken(id)).willReturn(token);
		given(token.getTreasury()).willReturn(treasury);
		given(store.loadTokenRelationship(token, treasury)).willReturn(treasuryRel);
		given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
		// when:
		subject.doStateTransition();

		// then:
		verify(token).burn(treasuryRel, amount);
		verify(store).persistToken(token);
		verify(store).persistTokenRelationship(treasuryRel);
	}

	@Test
	void followsHappyPathForUnique(){
		// setup:
		treasuryRel = new TokenRelationship(token, treasury);

		givenValidUniqueTxnCtx();
		given(accessor.getTxn()).willReturn(tokenBurnTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(store.loadToken(id)).willReturn(token);
		given(token.getTreasury()).willReturn(treasury);
		given(store.loadTokenRelationship(token, treasury)).willReturn(treasuryRel);
		given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
		// when:
		subject.doStateTransition();

		// then:
		verify(token).getType();
		verify(token).burn(any(OwnershipTracker.class), eq(treasuryRel), any(List.class));
		verify(store).persistToken(token);
		verify(store).persistTokenRelationship(treasuryRel);
		verify(store).persistTrackers(any(OwnershipTracker.class));
		verify(accountStore).persistAccount(any(Account.class));
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenBurnTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(tokenBurnTxn));
	}

	@Test
	void rejectsMissingToken() {
		givenMissingToken();

		// expect:
		assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenBurnTxn));
	}

	@Test
	void rejectsInvalidNegativeAmount() {
		givenInvalidNegativeAmount();

		// expect:
		assertEquals(INVALID_TOKEN_BURN_AMOUNT, subject.semanticCheck().apply(tokenBurnTxn));
	}

	@Test
	void rejectsInvalidZeroAmount() {
		givenInvalidZeroAmount();

		// expect:
		assertEquals(INVALID_TOKEN_BURN_AMOUNT, subject.semanticCheck().apply(tokenBurnTxn));
	}

	@Test
	void rejectsInvalidTxnBodyWithBothProps(){
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(
						TokenBurnTransactionBody.newBuilder()
								.addAllSerialNumbers(List.of(1L))
								.setAmount(1)
								.setToken(grpcId))
				.build();

		assertEquals(INVALID_TRANSACTION_BODY, subject.semanticCheck().apply(tokenBurnTxn));
	}


	@Test
	void rejectsInvalidTxnBodyWithNoProps(){
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(
						TokenBurnTransactionBody.newBuilder()
								.setToken(grpcId))
				.build();

		assertEquals(INVALID_TOKEN_BURN_AMOUNT, subject.semanticCheck().apply(tokenBurnTxn));
	}

	@Test
	void rejectsInvalidTxnBodyWithInvalidBatch(){
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(
						TokenBurnTransactionBody.newBuilder()
								.addAllSerialNumbers(LongStream.range(-20L, 0L).boxed().collect(Collectors.toList()))
								.setToken(grpcId))
				.build();

		given(validator.maxBatchSizeBurnCheck(tokenBurnTxn.getTokenBurn().getSerialNumbersCount())).willReturn(OK);
		assertEquals(INVALID_NFT_ID, subject.semanticCheck().apply(tokenBurnTxn));
	}

	private void givenValidTxnCtx() {
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(TokenBurnTransactionBody.newBuilder()
						.setToken(grpcId)
						.setAmount(amount))
				.build();
	}

	private void givenValidUniqueTxnCtx() {
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(TokenBurnTransactionBody.newBuilder()
						.setToken(grpcId)
						.addAllSerialNumbers(List.of(1L)))
				.build();
	}

	private void givenMissingToken() {
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(
						TokenBurnTransactionBody.newBuilder()
								.build()
				).build();
	}

	private void givenInvalidNegativeAmount() {
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(
						TokenBurnTransactionBody.newBuilder()
								.setToken(grpcId)
								.setAmount(-1)
								.build()
				).build();
	}

	private void givenInvalidZeroAmount() {
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(
						TokenBurnTransactionBody.newBuilder()
								.setToken(grpcId)
								.setAmount(0)
								.build()
				).build();
	}
}
