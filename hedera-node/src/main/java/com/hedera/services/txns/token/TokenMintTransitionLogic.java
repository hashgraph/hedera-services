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
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.unique.UniqueStore;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

/**
 * Provides the state transition for token minting.
 *
 * @author Michael Tinker
 */
public class TokenMintTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenMintTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final OptionValidator validator;
	private final TokenStore commonStore;
	private final UniqueStore uniqueStore;
	private final TransactionContext txnCtx;

	public TokenMintTransitionLogic(
			OptionValidator validator,
			TokenStore commonStore,
			UniqueStore uniqueStore,
			TransactionContext txnCtx
	) {
		this.validator = validator;
		this.commonStore = commonStore;
		this.uniqueStore = uniqueStore;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		try {
			var op = txnCtx.accessor().getTxn().getTokenMint();
			var id = commonStore.resolve(op.getToken());
			if (id == TokenStore.MISSING_TOKEN) {
				txnCtx.setStatus(INVALID_TOKEN_ID);
			} else {
				var token = commonStore.get(id);
				ResponseCodeEnum outcome;
				CreationResult<List<Long>> nftMintOutcome = null;
				if(token.tokenType().equals(TokenType.FUNGIBLE_COMMON)) {
					outcome = commonStore.mint(id, op.getAmount());
				} else {
					nftMintOutcome = uniqueStore.mint(op, RichInstant.fromJava(txnCtx.consensusTime()));
					outcome = nftMintOutcome.getStatus();
				}
				txnCtx.setStatus((outcome == OK) ? SUCCESS : outcome);
				if (outcome == OK) {
					txnCtx.setNewTotalSupply(commonStore.get(id).totalSupply());
					if (token.tokenType().equals(TokenType.NON_FUNGIBLE_UNIQUE)) {
						txnCtx.setCreated(nftMintOutcome.getCreated().get());
					}
				}
			}
		} catch (Exception e) {
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxn4Log(), e);
			txnCtx.setStatus(FAIL_INVALID);
		}
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
