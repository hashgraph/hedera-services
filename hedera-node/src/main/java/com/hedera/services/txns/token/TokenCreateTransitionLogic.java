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
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hedera.services.txns.validation.TokenListChecks.suppliesCheck;
import static com.hedera.services.txns.validation.TokenListChecks.supplyTypeCheck;
import static com.hedera.services.txns.validation.TokenListChecks.typeCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
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

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final OptionValidator validator;
	private final TokenStore store;
	private final HederaLedger ledger;
	private final TransactionContext txnCtx;
	private final GlobalDynamicProperties dynamicProperties;

	public TokenCreateTransitionLogic(
			OptionValidator validator,
			TokenStore store,
			HederaLedger ledger,
			TransactionContext txnCtx,
			GlobalDynamicProperties dynamicProperties
	) {
		this.validator = validator;
		this.store = store;
		this.ledger = ledger;
		this.txnCtx = txnCtx;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void doStateTransition() {
		try {
			final var op = txnCtx.accessor().getTxn().getTokenCreation();
			if (op.hasExpiry() && !validator.isValidExpiry(op.getExpiry())) {
				txnCtx.setStatus(INVALID_EXPIRATION_TIME);
				return;
			}
			transitionFor(op);
		} catch (Exception e) {
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxnWrapper(), e);
			abortWith(FAIL_INVALID);
		}
	}

	private void transitionFor(TokenCreateTransactionBody op) {
		var result = store.createProvisionally(op, txnCtx.activePayer(), txnCtx.consensusTime().getEpochSecond());
		if (result.getStatus() != OK) {
			abortWith(result.getStatus());
			return;
		}

		TokenID created;
		if (result.getCreated().isPresent()) {
			created = result.getCreated().get();
		} else {
			log.warn("TokenStore#createProvisionally contract broken, no created id for OK response!");
			abortWith(FAIL_INVALID);
			return;
		}

		var treasury = op.getTreasury();
		var status = autoEnableAccountForNewToken(treasury, created, op);
		if (status != OK) {
			abortWith(status);
			return;
		}

		final Set<AccountID> customCollectorsEnabled = new HashSet<>();
		customCollectorsEnabled.add(treasury);

		if (op.getCustomFeesCount() > 0) {
			status = autoEnableFeeCollectors(created, customCollectorsEnabled, op);
			if (status != OK) {
				abortWith(status);
				return;
			}
		}

		if (op.getTokenType() != TokenType.NON_FUNGIBLE_UNIQUE) {
			status = ledger.adjustTokenBalance(treasury, created, op.getInitialSupply());
			if (status != OK) {
				abortWith(status);
				return;
			}
		}

		List<FcTokenAssociation> newTokenAssociations = buildNewTokenAssociationList(customCollectorsEnabled, created);

		store.commitCreation();
		txnCtx.setNewTokenAssociations(newTokenAssociations);
		txnCtx.setCreated(created);
		txnCtx.setStatus(SUCCESS);
	}

	private List<FcTokenAssociation> buildNewTokenAssociationList(
			final Set<AccountID> customCollectorsEnabled, TokenID tokenID) {
		return customCollectorsEnabled.stream().map(
				accountID -> new FcTokenAssociation(
						EntityId.fromGrpcTokenId(tokenID),
						EntityId.fromGrpcAccountId(accountID))).collect(Collectors.toList());
	}

	private ResponseCodeEnum autoEnableFeeCollectors(
			TokenID created,
			Set<AccountID> customCollectorsEnabled,
			TokenCreateTransactionBody op
	) {
		/* TokenStore.associate() returns TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT if the
		account-token relationship already exists. */
		ResponseCodeEnum status = OK;
		for (var fee : op.getCustomFeesList()) {
			boolean collectorEnablementNeeded = fee.hasFractionalFee();
			if (fee.hasFixedFee() && fee.getFixedFee().hasDenominatingTokenId()) {
				collectorEnablementNeeded |= (0 == fee.getFixedFee().getDenominatingTokenId().getTokenNum());
			} else if (fee.hasRoyaltyFee() && fee.getRoyaltyFee().hasFallbackFee()) {
				final var fallback = fee.getRoyaltyFee().getFallbackFee();
				if (fallback.hasDenominatingTokenId()) {
					collectorEnablementNeeded |= (0 == fallback.getDenominatingTokenId().getTokenNum());
				}
			}
			final var collector = fee.getFeeCollectorAccountId();
			if (collectorEnablementNeeded && !customCollectorsEnabled.contains(collector)) {
				status = autoEnableAccountForNewToken(collector, created, op);
				if (status != OK) {
					return status;
				}
				customCollectorsEnabled.add(collector);
			}
		}
		return status;
	}

	private ResponseCodeEnum autoEnableAccountForNewToken(
			AccountID id,
			TokenID created,
			TokenCreateTransactionBody op
	) {
		var status = store.associate(id, List.of(created));
		if (status != OK) {
			return status;
		}
		if (op.hasFreezeKey()) {
			status = ledger.unfreeze(id, created);
		}
		if (status == OK && op.hasKycKey()) {
			status = ledger.grantKyc(id, created);
		}
		return status;
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
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenCreateTransactionBody op = txnBody.getTokenCreation();

		if (op.getTokenType() == TokenType.NON_FUNGIBLE_UNIQUE && !dynamicProperties.areNftsEnabled()) {
			return NOT_SUPPORTED;
		}

		var validity = validator.memoCheck(op.getMemo());
		if (validity != OK) {
			return validity;
		}

		validity = validator.tokenSymbolCheck(op.getSymbol());
		if (validity != OK) {
			return validity;
		}

		validity = validator.tokenNameCheck(op.getName());
		if (validity != OK) {
			return validity;
		}

		validity = typeCheck(op.getTokenType(), op.getInitialSupply(), op.getDecimals());
		if (validity != OK) {
			return validity;
		}

		validity = supplyTypeCheck(op.getSupplyType(), op.getMaxSupply());
		if (validity != OK) {
			return validity;
		}

		validity = suppliesCheck(op.getInitialSupply(), op.getMaxSupply());
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
				op.hasFreezeKey(), op.getFreezeKey(),
				op.hasFeeScheduleKey(), op.getFeeScheduleKey());
		if (validity != OK) {
			return validity;
		}

		if (op.getFreezeDefault() && !op.hasFreezeKey()) {
			return TOKEN_HAS_NO_FREEZE_KEY;
		}
		return validateAutoRenewAccount(op);
	}

	private ResponseCodeEnum validateAutoRenewAccount(final TokenCreateTransactionBody op) {
		ResponseCodeEnum validity = OK;
		if (op.hasAutoRenewAccount()) {
			validity = validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod()) ? OK : INVALID_RENEWAL_PERIOD;
			return validity;
		} else {
			if (op.getExpiry().getSeconds() <= txnCtx.consensusTime().getEpochSecond()) {
				return INVALID_EXPIRATION_TIME;
			}
		}
		return validity;
	}
}
