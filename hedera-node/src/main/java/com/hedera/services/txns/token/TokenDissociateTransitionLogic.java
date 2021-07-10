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
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.validation.TokenListChecks.repeatsItself;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;

/**
 * Provides the state transition for dissociating tokens from an account.
 */
public class TokenDissociateTransitionLogic implements TransitionLogic {
	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final TransactionContext txnCtx;
	private final OptionValidator validator;

	public TokenDissociateTransitionLogic(
			TypedTokenStore tokenStore,
			AccountStore accountStore,
			TransactionContext txnCtx,
			OptionValidator validator
	) {
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
		this.txnCtx = txnCtx;
		this.validator = validator;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		var op = txnCtx.accessor().getTxn().getTokenDissociate();
		final var accountId = Id.fromGrpcAccount(op.getAccount());

		/* --- Load the model objects --- */
		final var account = accountStore.loadAccount(accountId);
		final List<Pair<TokenRelationship, TokenRelationship>> tokenRelationships = new ArrayList<>();
		for (final var tokenId : op.getTokensList()) {
			final var token = tokenStore.loadPossiblyDeletedOrAutoRemovedToken(Id.fromGrpcToken(tokenId));
			var accountRelationship = tokenStore.loadTokenRelationship(token, account);
			var treasuryRelationship = tokenStore.loadTokenRelationship(token, token.getTreasury());
			tokenRelationships.add(Pair.of(accountRelationship, treasuryRelationship));
		}

		/* --- Do the business logic --- */
		account.dissociateWith(tokenRelationships, validator);

		/* --- Persist the updated models --- */
		accountStore.persistAccount(account);
		for (Pair<TokenRelationship, TokenRelationship> accountAndTreasuryPair : tokenRelationships) {
			tokenStore.persistToken(accountAndTreasuryPair.getKey().getToken());
			accountStore.persistAccount(accountAndTreasuryPair.getKey().getToken().getTreasury());
		}
		var flatTokenRels = new ArrayList<TokenRelationship>();
		for (var pair : tokenRelationships) {
			flatTokenRels.add(pair.getValue());
			flatTokenRels.add(pair.getKey());
		}
		tokenStore.persistTokenRelationships(flatTokenRels);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenDissociate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenDissociateTransactionBody op = txnBody.getTokenDissociate();

		if (!op.hasAccount()) {
			return INVALID_ACCOUNT_ID;
		}

		if (repeatsItself(op.getTokensList())) {
			return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
		}

		return OK;
	}
}
