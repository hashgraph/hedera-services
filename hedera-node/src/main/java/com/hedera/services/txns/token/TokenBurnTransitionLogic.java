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
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Provides the state transition for token burning.
 */
@Singleton
public class TokenBurnTransitionLogic implements TransitionLogic {
	private final OptionValidator validator;
	private final TypedTokenStore tokenStore;
	private final TransactionContext txnCtx;
	private final AccountStore accountStore;
	private final GlobalDynamicProperties dynamicProperties;

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	@Inject
	public TokenBurnTransitionLogic(
			OptionValidator validator,
			AccountStore accountStore,
			TypedTokenStore tokenStore,
			TransactionContext txnCtx,
			GlobalDynamicProperties dynamicProperties
	) {
		this.validator = validator;
		this.tokenStore = tokenStore;
		this.txnCtx = txnCtx;
		this.accountStore = accountStore;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenBurn();
		final var grpcId = op.getToken();
		final var targetId = Id.fromGrpcToken(grpcId);

		/* --- Load the model objects --- */
		final var token = tokenStore.loadToken(targetId);
		final var treasuryRel = tokenStore.loadTokenRelationship(token, token.getTreasury());
		final var ownershipTracker = new OwnershipTracker();

		/* --- Do the business logic --- */
		if (token.getType().equals(TokenType.FUNGIBLE_COMMON)) {
			token.burn(treasuryRel, op.getAmount());
		} else {
			final var burnList = op.getSerialNumbersList();
			tokenStore.loadUniqueTokens(token, burnList);
			token.burn(ownershipTracker, treasuryRel, burnList);
		}

		/* --- Persist the updated models --- */
		tokenStore.persistToken(token);
		tokenStore.persistTokenRelationships(List.of(treasuryRel));
		tokenStore.persistTrackers(ownershipTracker);
		accountStore.persistAccount(token.getTreasury());
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenBurn;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenBurnTransactionBody op = txnBody.getTokenBurn();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		final var numSerialNumbers = op.getSerialNumbersCount();
		if (numSerialNumbers > 0 && !dynamicProperties.areNftsEnabled()) {
			return NOT_SUPPORTED;
		}

		boolean bothPresent = (op.getAmount() > 0 && numSerialNumbers > 0);
		boolean nonePresent = (op.getAmount() <= 0 && numSerialNumbers == 0);
		if (nonePresent) {
			return INVALID_TOKEN_BURN_AMOUNT;
		}
		if (bothPresent) {
			return INVALID_TRANSACTION_BODY;
		}

		if (op.getAmount() <= 0 && op.getSerialNumbersCount() > 0) {
			return validateNfts(op);
		}
		return OK;
	}

	private ResponseCodeEnum validateNfts(final TokenBurnTransactionBody op) {
		var validity = validator.maxBatchSizeBurnCheck(op.getSerialNumbersCount());
		if (validity != OK) {
			return validity;
		}
		for (long serialNum : op.getSerialNumbersList()) {
			if (serialNum <= 0) {
				return INVALID_NFT_ID;
			}
		}
		return OK;
	}
}
