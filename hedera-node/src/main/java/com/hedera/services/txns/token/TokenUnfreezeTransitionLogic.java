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
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class TokenUnfreezeTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenUnfreezeTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final TypedTokenStore tokenStore;
	private final TransactionContext txnCtx;

	public TokenUnfreezeTransitionLogic(
			TypedTokenStore tokenStore,
			TransactionContext txnCtx
	) {
		this.txnCtx = txnCtx;
		this.tokenStore = tokenStore;
	}

	@Override
	public void doStateTransition() {
		var op = txnCtx.accessor().getTxn().getTokenUnfreeze();
		tokenStore.unFreeze(op.getAccount(), op.getToken());
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
