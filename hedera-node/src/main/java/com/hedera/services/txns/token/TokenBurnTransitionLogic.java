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
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;

/**
 * Provides the state transition for token burning.
 */
@Singleton
public class TokenBurnTransitionLogic implements TransitionLogic {
	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final GlobalDynamicProperties dynamicProperties;
	private final BurnLogic burnLogic;

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	@Inject
	public TokenBurnTransitionLogic(
			OptionValidator validator,
			TransactionContext txnCtx,
			GlobalDynamicProperties dynamicProperties,
			BurnLogic burnLogic
	) {
		this.validator = validator;
		this.txnCtx = txnCtx;
		this.dynamicProperties = dynamicProperties;
		this.burnLogic = burnLogic;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenBurn();
		final var grpcId = op.getToken();
		final var targetId = Id.fromGrpcToken(grpcId);
		final var amount = op.getAmount();
		final var serialNumbersList = op.getSerialNumbersList();

		burnLogic.burn(targetId, amount, serialNumbersList);
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

		return validateTokenOpsWith(
				op.getSerialNumbersCount(),
				op.getAmount(),
				dynamicProperties.areNftsEnabled(),
				INVALID_TOKEN_BURN_AMOUNT,
				op.getSerialNumbersList(),
				validator::maxBatchSizeBurnCheck
		);
	}
}