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

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Provides the state transition for token minting.
 *
 * @author Michael Tinker
 */
public class TokenMintTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenMintTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final OptionValidator validator;
	private final TypedTokenStore tokenStore;
	private final TransactionContext txnCtx;
	private final AccountStore accountStore;

	public TokenMintTransitionLogic(
			OptionValidator validator,
			AccountStore accountStore,
			TypedTokenStore tokenStore,
			TransactionContext txnCtx
	) {
		this.validator = validator;
		this.tokenStore = tokenStore;
		this.txnCtx = txnCtx;
		this.accountStore = accountStore;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenMint();
		final var grpcId = op.getToken();
		final var targetId = new Id(grpcId.getShardNum(), grpcId.getRealmNum(), grpcId.getTokenNum());

		/* --- Load the model objects --- */
		final var token = tokenStore.loadToken(targetId);
		final var treasuryRel = tokenStore.loadTokenRelationship(token, token.getTreasury());

		/* --- Instantiate change trackers --- */
		final var ownershipTracker = new OwnershipTracker();

		/* --- Do the business logic --- */
		if (token.getType() == TokenType.FUNGIBLE_COMMON) {
			token.mint(treasuryRel, op.getAmount());
		} else {
			token.mint(ownershipTracker, treasuryRel, op.getMetadataList(), RichInstant.fromJava(txnCtx.consensusTime()));
		}

		/* --- Persist the updated models --- */
		tokenStore.persistToken(token);
		tokenStore.persistTokenRelationship(treasuryRel);
		tokenStore.persistTrackers(ownershipTracker);
		accountStore.persistAccount(token.getTreasury());
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

		boolean bothPresent = (op.getAmount() > 0 && op.getMetadataCount() > 0);
		boolean nonePresent = (op.getAmount() <= 0 && op.getMetadataCount() == 0);
		boolean onlyMetadataIsPresent = (op.getAmount() <= 0 && op.getMetadataCount() > 0);

		if (nonePresent) {
			return INVALID_TOKEN_MINT_AMOUNT;
		}

		if (bothPresent) {
			return INVALID_TRANSACTION_BODY;
		}

		if (onlyMetadataIsPresent) {
			var validity = validator.maxBatchSizeMintCheck(op.getMetadataCount());
			if (validity != OK) {
				return validity;
			}

			for (ByteString bytes : op.getMetadataList()) {
				validity = validator.nftMetadataCheck(bytes.toByteArray());
				if (validity != OK) {
					return validity;
				}
			}
		}

		return OK;
	}
}
