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
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.validation.TokenListChecks.repeatsItself;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;

/**
 * Provides the state transition for associating tokens to an account.
 */
@Singleton
public class TokenAssociateTransitionLogic implements TransitionLogic {
	private final Function<TransactionBody, ResponseCodeEnum> semanticCheck = this::validate;

	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final TransactionContext txnCtx;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public TokenAssociateTransitionLogic(
			AccountStore accountStore,
			TypedTokenStore tokenStore,
			TransactionContext txnCtx,
			GlobalDynamicProperties dynamicProperties
	) {
		this.dynamicProperties = dynamicProperties;
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenAssociate();
		/* First the account */
		final var grpcId = op.getAccount();
		final var accountId = new Id(grpcId.getShardNum(), grpcId.getRealmNum(), grpcId.getAccountNum());
		/* And then the tokens */
		final List<Id> tokenIds = new ArrayList<>();
		for (final var _grpcId : op.getTokensList()) {
			tokenIds.add(new Id(_grpcId.getShardNum(), _grpcId.getRealmNum(), _grpcId.getTokenNum()));
		}

		/* --- Load the model objects --- */
		final var account = accountStore.loadAccount(accountId);
		final List<Token> tokens = new ArrayList<>();
		for (final var tokenId : tokenIds) {
			final var token = tokenStore.loadToken(tokenId);
			tokens.add(token);
		}

		/* --- Do the business logic --- */
		account.associateWith(tokens, dynamicProperties.maxTokensPerAccount(), false);

		/* --- Persist the updated models --- */
		accountStore.persistAccount(account);
		for (final var token : tokens) {
			tokenStore.persistTokenRelationships(List.of(token.newRelationshipWith(account, false)));
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenAssociate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return semanticCheck;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenAssociateTransactionBody op = txnBody.getTokenAssociate();

		if (!op.hasAccount()) {
			return INVALID_ACCOUNT_ID;
		}

		if (repeatsItself(op.getTokensList())) {
			return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
		}

		return OK;
	}
}
