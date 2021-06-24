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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 *
 * @author Michael Tinker
 */
public class TokenDissociateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenDissociateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final TransactionContext txnCtx;

	public TokenDissociateTransitionLogic(
			TypedTokenStore tokenStore,
			AccountStore accountStore,
			TransactionContext txnCtx
	) {
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		var op = txnCtx.accessor().getTxn().getTokenDissociate();
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
		final List<TokenRelationship> tokenRelationships = new ArrayList<>();
		for (final var tokenId : tokenIds) {
			final var token = tokenStore.loadPossiblyDeletedToken(tokenId);
			var tokenRelationShip = tokenStore.loadTokenRelationship(token, account);
			tokenRelationships.add(tokenRelationShip);
		}

		/* --- Do the business logic --- */
		for(TokenRelationship tokenRelationship : tokenRelationships) {
			var token = tokenRelationship.getToken();
			var treasury = token.getTreasury();
			var treasuryRelationShip = tokenStore.loadTokenRelationship(token, treasury);
			tokenRelationship.validateAndDissociate(accountStore.getValidator(), treasuryRelationShip);
			account.dissociateWith(List.of(token));

			/* --- Persist the updated models --- */
			accountStore.persistAccount(account);
			tokenStore.persistTokenRelationships(List.of(treasuryRelationShip, tokenRelationship));
		}
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
