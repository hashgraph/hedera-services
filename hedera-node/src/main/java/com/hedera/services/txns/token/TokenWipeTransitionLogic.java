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
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Provides the state transition for wiping [part of] a token balance.
 *
 * @author Michael Tinker
 */
public class TokenWipeTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenWipeTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final TypedTokenStore tokenStore;
	private final AccountStore accountStore;
	private final TransactionContext txnCtx;

	public TokenWipeTransitionLogic(
			TypedTokenStore tokenStore,
			AccountStore accountStore,
			TransactionContext txnCtx
	) {
		this.tokenStore = tokenStore;
		this.txnCtx = txnCtx;
		this.accountStore = accountStore;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenWipe();
		final var grpcTokenId = op.getToken();
		final var grpcAccountId = op.getAccount();
		final var tokenId = new Id(grpcTokenId.getShardNum(), grpcTokenId.getRealmNum(), grpcTokenId.getTokenNum());
		final var accountId = new Id(grpcAccountId.getShardNum(), grpcAccountId.getRealmNum(), grpcAccountId.getAccountNum());


		/* --- Load the model objects --- */
		var token = tokenStore.loadToken(tokenId);
		var account = accountStore.loadAccount(accountId);
		var tokenRelationship = tokenStore.loadTokenRelationship(token, account);

		/* --- Do the business logic --- */
		token.wipe(tokenRelationship, op.getAmount(), false);

		/* --- Persist the updated models --- */
		tokenStore.persistTokenRelationship(tokenRelationship);
		tokenStore.persistToken(token);
	}


	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenWipe;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenWipeAccountTransactionBody op = txnBody.getTokenWipe();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		if (!op.hasAccount()) {
			return INVALID_ACCOUNT_ID;
		}

		if (op.getAmount() <= 0) {
			return INVALID_WIPING_AMOUNT;
		}

		return OK;
	}
}
