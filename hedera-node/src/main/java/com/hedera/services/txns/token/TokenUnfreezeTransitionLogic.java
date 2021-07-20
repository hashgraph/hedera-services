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
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class TokenUnfreezeTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenUnfreezeTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final TypedTokenStore tokenStore;
	private final TransactionContext txnCtx;
	private final AccountStore accountStore;

	public TokenUnfreezeTransitionLogic(
			TypedTokenStore tokenStore,
			AccountStore accountStore,
			TransactionContext txnCtx
	) {
		this.txnCtx = txnCtx;
		this.tokenStore = tokenStore;
		this.accountStore = accountStore;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenUnfreeze();
		final var grpcTokenId = op.getToken();
		final var grpcAccountId = op.getAccount();
		final var targetTokenId = new Id(
				grpcTokenId.getShardNum(),
				grpcTokenId.getRealmNum(),
				grpcTokenId.getTokenNum());
		final var targetAccountId = new Id(
				grpcAccountId.getShardNum(),
				grpcAccountId.getRealmNum(),
				grpcAccountId.getAccountNum());

		/* --- Load the model objects --- */
		final var token = tokenStore.loadToken(targetTokenId);
		final var account = accountStore.loadAccount(targetAccountId);
		final var tokenRel = tokenStore.loadTokenRelationship(token, account);

		/* --- Do the business logic --- */
		tokenRel.updateForzen(false);

		/* --- Persist the updated models --- */
		tokenStore.persistTokenRelationship(tokenRel);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenUnfreeze;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenUnfreezeAccountTransactionBody op = txnBody.getTokenUnfreeze();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		if (!op.hasAccount()) {
			return INVALID_ACCOUNT_ID;
		}

		return OK;
	}
}
