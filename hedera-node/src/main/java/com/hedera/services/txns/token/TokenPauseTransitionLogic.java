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
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Provides the state transition for pausing the Token.
 */
@Singleton
public class TokenPauseTransitionLogic implements TransitionLogic {
	private final TypedTokenStore tokenStore;
	private final TransactionContext txnCtx;

	@Inject
	public TokenPauseTransitionLogic(
			final TypedTokenStore tokenStore,
			final TransactionContext txnCtx
	) {
		this.txnCtx = txnCtx;
		this.tokenStore = tokenStore;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		var op = txnCtx.accessor().getTxn().getTokenPause();
		var grpcTokenId = op.getToken();
		var targetTokenId = Id.fromGrpcToken(grpcTokenId);

		/* --- Load the model objects --- */
		var token = tokenStore.loadPossiblyPausedToken(targetTokenId);

		/* --- Do the business logic --- */
		token.changePauseStatus(true);

		/* --- Persist the updated models --- */
		tokenStore.commitToken(token);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenPause;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenPauseTransactionBody op = txnBody.getTokenPause();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		return OK;
	}
}
