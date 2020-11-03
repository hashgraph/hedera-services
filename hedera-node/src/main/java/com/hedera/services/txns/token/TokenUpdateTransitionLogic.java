package com.hedera.services.txns.token;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.tokens.TokenStore;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.tokens.TokenStore.MISSING_TOKEN;
import static com.hedera.services.txns.validation.TokenListChecks.checkKey;
import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

/**
 * Provides the state transition for token updates.
 *
 * @author Michael Tinker
 */
public class TokenUpdateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenUpdateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

	private final OptionValidator validator;
	private final TokenStore store;
	private final HederaLedger ledger;
	private final TransactionContext txnCtx;
	private final Predicate<TokenUpdateTransactionBody> affectsExpiryOnly;

	public TokenUpdateTransitionLogic(
			OptionValidator validator,
			TokenStore store,
			HederaLedger ledger,
			TransactionContext txnCtx,
			Predicate<TokenUpdateTransactionBody> affectsExpiryOnly
	) {
		this.validator = validator;
		this.store = store;
		this.ledger = ledger;
		this.txnCtx = txnCtx;
		this.affectsExpiryOnly = affectsExpiryOnly;
	}

	@Override
	public void doStateTransition() {
		try {
			transitionFor(txnCtx.accessor().getTxn().getTokenUpdate());
		} catch (Exception e) {
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxn4Log(), e);
			abortWith(FAIL_INVALID);
		}
	}

	private void transitionFor(TokenUpdateTransactionBody op) {
		var id = store.resolve(op.getToken());
		if (id == MISSING_TOKEN) {
			txnCtx.setStatus(INVALID_TOKEN_ID);
			return;
		}

		var outcome = OK;
		MerkleToken token = store.get(id);

		if (token.adminKey().isEmpty() && !affectsExpiryOnly.test(op)) {
			txnCtx.setStatus(TOKEN_IS_IMMUTABLE);
			return;
		}

		if (token.isDeleted()) {
			txnCtx.setStatus(TOKEN_WAS_DELETED);
			return;
		}

		Optional<AccountID> replacedTreasury = Optional.empty();
		if (op.hasTreasury()) {
			var newTreasury = op.getTreasury();
			if (!ledger.exists(newTreasury) || ledger.isDeleted(newTreasury)) {
				txnCtx.setStatus(INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
				return;
			}
			var existingTreasury = token.treasury().toGrpcAccountId();
			if (!newTreasury.equals(existingTreasury)) {
				outcome = prepNewTreasury(id, token, newTreasury);
				if (outcome != OK) {
					abortWith(outcome);
					return;
				}
				replacedTreasury = Optional.of(token.treasury().toGrpcAccountId());
			}
		}

		outcome = store.update(op, txnCtx.consensusTime().getEpochSecond());
		if (outcome == OK && replacedTreasury.isPresent()) {
			long replacedTreasuryBalance = ledger.getTokenBalance(replacedTreasury.get(), id);
			outcome = ledger.doTokenTransfer(
					id,
					replacedTreasury.get(),
					op.getTreasury(),
					replacedTreasuryBalance,
					true);
		}
		if (outcome != OK) {
			abortWith(outcome);
			return;
		}

		txnCtx.setStatus(SUCCESS);
	}

	private ResponseCodeEnum prepNewTreasury(TokenID id, MerkleToken token, AccountID newTreasury) {
		var status = OK;
		if (token.hasFreezeKey()) {
			status = ledger.unfreeze(newTreasury, id);
		}
		if (status == OK && token.hasKycKey()) {
			status = ledger.grantKyc(newTreasury, id);
		}
		return status;
	}

	private void abortWith(ResponseCodeEnum cause) {
		ledger.dropPendingTokenChanges();
		txnCtx.setStatus(cause);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenUpdate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenUpdateTransactionBody op = txnBody.getTokenUpdate();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		var validity = OK;

		var hasNewSymbol = op.getSymbol().length() > 0;
		if (hasNewSymbol) {
			validity = validator.tokenSymbolCheck(op.getSymbol());
			if (validity != OK) {
				return validity;
			}
		}

		var hasNewTokenName = op.getName().length() > 0;
		if (hasNewTokenName) {
			validity = validator.tokenNameCheck(op.getName());
			if (validity != OK) {
				return validity;
			}
		}

		validity = checkKeys(
				op.hasAdminKey(), op.getAdminKey(),
				op.hasKycKey(), op.getKycKey(),
				op.hasWipeKey(), op.getWipeKey(),
				op.hasSupplyKey(), op.getSupplyKey(),
				op.hasFreezeKey(), op.getFreezeKey());
		if (validity != OK) {
			return validity;
		}

		return validity;
	}
}
