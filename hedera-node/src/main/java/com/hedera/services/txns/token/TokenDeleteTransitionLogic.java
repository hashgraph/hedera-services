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
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Provides the state transition for token deletion.
 */
@Singleton
public class TokenDeleteTransitionLogic implements TransitionLogic {
	private final TransactionContext txnCtx;
	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final SigImpactHistorian sigImpactHistorian;

	@Inject
	public TokenDeleteTransitionLogic(
			final TransactionContext txnCtx,
			final AccountStore accountStore,
			final TypedTokenStore tokenStore,
			final SigImpactHistorian sigImpactHistorian
	) {
		this.txnCtx = txnCtx;
		this.tokenStore = tokenStore;
		this.accountStore = accountStore;
		this.sigImpactHistorian = sigImpactHistorian;
	}

	@Override
	public void doStateTransition() {
		// --- Translate from gRPC types ---
		final var op = txnCtx.accessor().getTxn().getTokenDeletion();
		final var grpcTokenId = op.getToken();

		// --- Convert to model id ---
		final var targetTokenId = Id.fromGrpcToken(grpcTokenId);

		// --- Load the model object ---
		final var loadedToken = tokenStore.loadToken(targetTokenId);

		// --- Do the business logic ---
		loadedToken.delete();

		// --- Persist the updated model ---
		tokenStore.commitToken(loadedToken);
		accountStore.commitAccount(loadedToken.getTreasury());
		sigImpactHistorian.markEntityChanged(grpcTokenId.getTokenNum());
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenDeletion;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	public ResponseCodeEnum validate(final TransactionBody txnBody) {
		final TokenDeleteTransactionBody op = txnBody.getTokenDeletion();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		return OK;
	}
}


