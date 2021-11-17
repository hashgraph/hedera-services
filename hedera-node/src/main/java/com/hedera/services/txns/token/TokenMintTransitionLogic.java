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
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;

/**
 * Provides the state transition for token minting.
 */
@Singleton
public class TokenMintTransitionLogic implements TransitionLogic {
	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final OptionValidator validator;
	private final TypedTokenStore tokenStore;
	private final TransactionContext txnCtx;
	private final AccountStore accountStore;
	private final GlobalDynamicProperties dynamicProperties;
	private final MintLogic mintLogic;

	@Inject
	public TokenMintTransitionLogic(
			OptionValidator validator,
			AccountStore accountStore,
			TypedTokenStore tokenStore,
			TransactionContext txnCtx,
			GlobalDynamicProperties dynamicProperties,
			MintLogic mintLogic
	) {
		this.validator = validator;
		this.tokenStore = tokenStore;
		this.txnCtx = txnCtx;
		this.accountStore = accountStore;
		this.dynamicProperties = dynamicProperties;
		this.mintLogic = mintLogic;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenMint();
		final var grpcId = op.getToken();
		final var targetId = Id.fromGrpcToken(grpcId);

		/* --- Load the model objects --- */
		final var token = tokenStore.loadToken(targetId);

		mintLogic.mint(validator,
				accountStore,
				tokenStore,
				token,
				op,
				txnCtx.consensusTime());
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenMint;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenMintTransactionBody op = txnBody.getTokenMint();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		return validateTokenOpsWith(
				op.getMetadataCount(),
				op.getAmount(),
				dynamicProperties.areNftsEnabled(),
				INVALID_TOKEN_MINT_AMOUNT,
				op.getMetadataList(),
				validator::maxBatchSizeMintCheck,
				validator::nftMetadataCheck
		);
	}
}
