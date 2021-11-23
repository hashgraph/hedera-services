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
import com.hedera.services.context.properties.GlobalDynamicProperties;
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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
class TokenBurnTransitionLogicTest {
	private final long amount = 123L;
	private final TokenID grpcId = IdUtils.asToken("1.2.3");
	private final Id id = new Id(1, 2, 3);
	private final Id treasuryId = new Id(2, 4, 6);
	private final Account treasury = new Account(treasuryId);

	@Mock
	private TransactionContext txnCtx;

	@Mock
	private OptionValidator validator;
	@Mock
	private GlobalDynamicProperties dynamicProperties;

	private TransactionBody tokenBurnTxn;

	private TokenBurnTransitionLogic subject;
	@Mock
	private BurnLogic burnLogic;

	@BeforeEach
	private void setup() {
		subject = new TokenBurnTransitionLogic(validator, txnCtx, dynamicProperties, burnLogic);
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
	void rejectsUniqueWhenNftsNotEnabled() {
		givenValidUniqueTxnCtx();
		given(dynamicProperties.areNftsEnabled()).willReturn(false);

		// expect:
		assertEquals(NOT_SUPPORTED, subject.semanticCheck().apply(tokenBurnTxn));
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
	void rejectsInvalidTxnBodyWithBothProps() {
		given(dynamicProperties.areNftsEnabled()).willReturn(true);

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
	void rejectsInvalidTxnBodyWithNoProps() {
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(
						TokenBurnTransactionBody.newBuilder()
								.setToken(grpcId))
				.build();

		assertEquals(INVALID_TOKEN_BURN_AMOUNT, subject.semanticCheck().apply(tokenBurnTxn));
	}

	@Test
	void rejectsInvalidTxnBodyWithInvalidBatch() {
		given(dynamicProperties.areNftsEnabled()).willReturn(true);
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(
						TokenBurnTransactionBody.newBuilder()
								.addAllSerialNumbers(LongStream.range(-20L, 0L).boxed().collect(Collectors.toList()))
								.setToken(grpcId))
				.build();

		given(validator.maxBatchSizeBurnCheck(tokenBurnTxn.getTokenBurn().getSerialNumbersCount())).willReturn(OK);
		assertEquals(INVALID_NFT_ID, subject.semanticCheck().apply(tokenBurnTxn));
	}

	@Test
	void propagatesErrorOnBatchSizeExceeded() {
		given(dynamicProperties.areNftsEnabled()).willReturn(true);
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(
						TokenBurnTransactionBody.newBuilder()
								.addAllSerialNumbers(LongStream.range(1, 5).boxed().collect(Collectors.toList()))
								.setToken(grpcId))
				.build();

		given(validator.maxBatchSizeBurnCheck(tokenBurnTxn.getTokenBurn().getSerialNumbersCount())).willReturn(
				BATCH_SIZE_LIMIT_EXCEEDED);
		assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.semanticCheck().apply(tokenBurnTxn));
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
