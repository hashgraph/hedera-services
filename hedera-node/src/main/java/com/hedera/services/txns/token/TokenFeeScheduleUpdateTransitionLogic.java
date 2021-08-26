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
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.fees.CustomFee;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.CustomFeeValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.store.AccountStore;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;

/**
 * Provides the state transition for updating token fee schedule.
 */
public class TokenFeeScheduleUpdateTransitionLogic implements TransitionLogic {
	private final TypedTokenStore typedTokenStore;
	private final AccountStore accountStore;
	private final TransactionContext txnCtx;
	private final GlobalDynamicProperties globalDynamicProperties;

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	public TokenFeeScheduleUpdateTransitionLogic(
			final TypedTokenStore tokenStore,
			final TransactionContext txnCtx,
			final AccountStore accountStore,
			final GlobalDynamicProperties globalDynamicProperties) {
		this.typedTokenStore = tokenStore;
		this.accountStore = accountStore;
		this.txnCtx = txnCtx;
		this.globalDynamicProperties = globalDynamicProperties;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		var op = txnCtx.accessor().getTxn().getTokenFeeScheduleUpdate();
		var grpcTokenId = op.getTokenId();
		var targetTokenId = Id.fromGrpcToken(grpcTokenId);

		/* --- Load the model objects --- */
		var token = typedTokenStore.loadToken(targetTokenId);

		/* --- Validate and initialize custom fees list --- */
		validateFalse(op.getCustomFeesCount() > globalDynamicProperties.maxCustomFeesAllowed(), CUSTOM_FEES_LIST_TOO_LONG);
		final var customFeesList = new ArrayList<CustomFee>();
		for (final var grpcFee : op.getCustomFeesList()) {
			final var collectorId = Id.fromGrpcAccount(grpcFee.getFeeCollectorAccountId());
			final var collector = accountStore.loadAccountOrFailWith(collectorId, INVALID_CUSTOM_FEE_COLLECTOR);

			var fee = validateAndInitCustomFee(grpcFee, collector, token.getType());
			customFeesList.add(fee);
		}
		token.setCustomFees(customFeesList);

		/* --- Persist the updated models --- */
		this.typedTokenStore.persistToken(token);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenFeeScheduleUpdate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	private ResponseCodeEnum validate(TransactionBody txnBody) {
		final var op = txnBody.getTokenFeeScheduleUpdate();
		if (!op.hasTokenId()) {
			return INVALID_TOKEN_ID;
		}

		return OK;
	}

	private CustomFee validateAndInitCustomFee(com.hederahashgraph.api.proto.java.CustomFee grpcFee, Account collector, TokenType tokenType){
		final var fee = CustomFee.fromGrpc(grpcFee, collector);

		if (grpcFee.hasFixedFee()) {
			if(grpcFee.getFixedFee().hasDenominatingTokenId()){
				final var grpcDenomId = grpcFee.getFixedFee().getDenominatingTokenId();
				final var denominatingToken = typedTokenStore.loadTokenOrFailWith(
						Id.fromGrpcToken(grpcDenomId), INVALID_TOKEN_ID_IN_CUSTOM_FEES);
				CustomFeeValidator.validateFixedFee(grpcFee, fee, denominatingToken, collector);
				CustomFeeValidator.initFixedFee(grpcFee, fee, denominatingToken.getId());
			}else{
				CustomFeeValidator.validateFixedFee(grpcFee, fee, null, collector);
			}

		} else if (grpcFee.hasRoyaltyFee()) {
			var grpcRoyaltyFee = grpcFee.getRoyaltyFee();
			if(grpcRoyaltyFee.getFallbackFee().hasDenominatingTokenId()){
				typedTokenStore.loadTokenOrFailWith(
						Id.fromGrpcToken(grpcRoyaltyFee.getFallbackFee().getDenominatingTokenId()),
						INVALID_TOKEN_ID_IN_CUSTOM_FEES);
			} else {
				if(fee.getRoyaltyFee().getFallbackFee() != null) {
					fee.getRoyaltyFee().getFallbackFee().setDenominatingTokenId(null);
				}
			}
			CustomFeeValidator.validateRoyaltyFee(grpcFee, tokenType, collector);
			initRoyaltyFee(grpcFee, fee);
		}

		return fee;
	}

	private void initRoyaltyFee(
			com.hederahashgraph.api.proto.java.CustomFee grpcFee,
			CustomFee fee
	) {
		final var grpcRoyaltyFee = grpcFee.getRoyaltyFee();
		final var fallbackGrpc = grpcRoyaltyFee.getFallbackFee();
		if (fallbackGrpc.hasDenominatingTokenId()) {
			final var denomTokenId = fallbackGrpc.getDenominatingTokenId();
			if (denomTokenId.getTokenNum() != 0) {
				fee.getRoyaltyFee().getFallbackFee().setDenominatingTokenId(Id.fromGrpcToken(denomTokenId));
			} else {
				fee.getRoyaltyFee().getFallbackFee().setDenominatingTokenId(null);
			}
		}else{
			fee.getRoyaltyFee().getFallbackFee().setDenominatingTokenId(null);
		}
	}
}
