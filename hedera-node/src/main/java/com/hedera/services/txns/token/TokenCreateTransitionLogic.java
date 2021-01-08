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
import com.hedera.services.tokens.TokenStore;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hedera.services.txns.validation.TokenListChecks.initialSupplyAndDecimalsCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;

/**
 * Provides the state transition for token creation.
 *
 * @author Michael Tinker
 */
public class TokenCreateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenCreateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

	private final OptionValidator validator;
	private final TokenStore store;
	private final HederaLedger ledger;
	private final TransactionContext txnCtx;

	public TokenCreateTransitionLogic(
			OptionValidator validator,
			TokenStore store,
			HederaLedger ledger,
			TransactionContext txnCtx
	) {
		this.validator = validator;
		this.store = store;
		this.ledger = ledger;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		try {
			transitionFor(txnCtx.accessor().getTxn().getTokenCreation());
		} catch (Exception e) {
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxn4Log(), e);
			abortWith(FAIL_INVALID);
		}
	}

	private void transitionFor(TokenCreateTransactionBody op) {
		var result = store.createProvisionally(op, txnCtx.activePayer(), txnCtx.consensusTime().getEpochSecond());
		if (result.getStatus() != OK) {
			abortWith(result.getStatus());
			return;
		}

		var created = result.getCreated().get();
		var treasury = op.getTreasury();
		var status = OK;
		status = store.associate(treasury, List.of(created));
		if (status != OK) {
			abortWith(status);
			return;
		}
		if (op.hasFreezeKey()) {
			status = ledger.unfreeze(treasury, created);
		}
		if (status == OK && op.hasKycKey()) {
			status = ledger.grantKyc(treasury, created);
		}
		if (status == OK) {
			status = ledger.adjustTokenBalance(treasury, created, op.getInitialSupply());
		}

		if (status != OK) {
			abortWith(status);
			return;
		}

		store.commitCreation();
		txnCtx.setCreated(created);
		txnCtx.setStatus(SUCCESS);
	}

	private void abortWith(ResponseCodeEnum cause) {
		if (store.isCreationPending()) {
			store.rollbackCreation();
		}
		ledger.dropPendingTokenChanges();
		txnCtx.setStatus(cause);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenCreation;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenCreateTransactionBody op = txnBody.getTokenCreation();

		var validity = validator.tokenSymbolCheck(op.getSymbol());
		if (validity != OK) {
			return validity;
		}

		validity = validator.tokenNameCheck(op.getName());
		if (validity != OK) {
			return validity;
		}

		validity = initialSupplyAndDecimalsCheck(op.getInitialSupply(), op.getDecimals());
		if (validity != OK) {
			return validity;
		}

		if (!op.hasTreasury()) {
			return INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
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

		if (op.getFreezeDefault() && !op.hasFreezeKey()) {
			return TOKEN_HAS_NO_FREEZE_KEY;
		}

		if (op.hasAutoRenewAccount()) {
			validity = validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod()) ? OK : INVALID_RENEWAL_PERIOD;
			return validity;
		} else {
			if (op.getExpiry().getSeconds() <= txnCtx.consensusTime().getEpochSecond()) {
				return INVALID_EXPIRATION_TIME;
			}
		}

		return OK;
	}
}
