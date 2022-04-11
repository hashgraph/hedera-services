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
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;

/**
 * Provides the state transition for wiping [part of] a token balance.
 */
@Singleton
public class TokenWipeTransitionLogic implements TransitionLogic {
	private final TransactionContext txnCtx;
	private final TypedTokenStore tokenStore;
	private final AccountStore accountStore;
	private final OptionValidator validator;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public TokenWipeTransitionLogic(
			final OptionValidator validator,
			final TypedTokenStore tokenStore,
			final AccountStore accountStore,
			final TransactionContext txnCtx,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.txnCtx = txnCtx;
		this.tokenStore = tokenStore;
		this.accountStore = accountStore;
		this.validator = validator;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenWipe();
		final var targetTokenId = Id.fromGrpcToken(op.getToken());
		final var targetAccountId = Id.fromGrpcAccount(op.getAccount());

		/* --- Load the model objects --- */
		final var token = tokenStore.loadToken(targetTokenId);
		final var account = accountStore.loadAccount(targetAccountId);
		final var accountRel = tokenStore.loadTokenRelationship(token, account);

		/* --- Instantiate change trackers --- */
		final var ownershipTracker = new OwnershipTracker();

		/* --- Do the business logic --- */
		if (token.getType().equals(TokenType.FUNGIBLE_COMMON)) {
			token.wipe(accountRel, op.getAmount());
		} else {
			tokenStore.loadUniqueTokens(token, op.getSerialNumbersList());
			token.wipe(ownershipTracker, accountRel, op.getSerialNumbersList());
		}
		/* --- Persist the updated models --- */
		tokenStore.commitToken(token);
		tokenStore.commitTokenRelationships(List.of(accountRel));
		tokenStore.commitTrackers(ownershipTracker);
		accountStore.commitAccount(account);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenWipe;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenWipeAccountTransactionBody op = txnBody.getTokenWipe();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		if (!op.hasAccount()) {
			return INVALID_ACCOUNT_ID;
		}
		return validateTokenOpsWith(
				op.getSerialNumbersCount(),
				op.getAmount(),
				dynamicProperties.areNftsEnabled(),
				INVALID_WIPING_AMOUNT,
				op.getSerialNumbersList(),
				validator::maxBatchSizeWipeCheck
		);
	}
}
