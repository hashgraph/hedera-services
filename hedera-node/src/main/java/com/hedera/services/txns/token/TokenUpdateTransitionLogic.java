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
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.tokens.annotations.AreTreasuryWildcardsEnabled;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

/**
 * Provides the state transition for token updates.
 */
@Singleton
public class TokenUpdateTransitionLogic implements TransitionLogic {

	private final boolean allowChangedTreasuryToOwnNfts;
	private final TransactionContext txnCtx;
	private final OptionValidator validator;
	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;
	private final TypedTokenStore tokenStore;
	private final AccountStore accountStore;

	@Inject
	public TokenUpdateTransitionLogic(
			@AreTreasuryWildcardsEnabled boolean allowChangedTreasuryToOwnNfts,
			OptionValidator validator,
			TransactionContext txnCtx,
			TypedTokenStore tokenStore,
			AccountStore accountStore
	) {
		this.validator = validator;
		this.txnCtx = txnCtx;
		this.allowChangedTreasuryToOwnNfts = allowChangedTreasuryToOwnNfts;
		this.tokenStore = tokenStore;
		this.accountStore = accountStore;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenUpdate();
		validateExpiry(op);

		/* --- Load model objects --- */
		final var token = tokenStore.loadToken(Id.fromGrpcToken(op.getToken()));
		validateTokenIsMutable(token, op);

		Account newAutoRenew = null;
		Account newTreasury = null;
		TokenRelationship newTreasuryRel = null;
		TokenRelationship currentTreasuryRel = tokenStore.loadTokenRelationship(token, token.getTreasury());
		boolean updatesTreasury = op.hasTreasury() && (!token.getTreasury().getId().equals(Id.fromGrpcAccount(op.getTreasury())));
		if (updatesTreasury) {
			newTreasury = accountStore.loadAccountOrFailWith(Id.fromGrpcAccount(op.getTreasury()), INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
			validateFalse(newTreasury.isSmartContract(), INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
			validateAssociationBetween(newTreasury, token);
			newTreasuryRel = tokenStore.loadTokenRelationship(token, newTreasury);
			if (token.getType() == NON_FUNGIBLE_UNIQUE) {
				validateTrue(newTreasuryRel.getBalance() == 0, TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
				if (!allowChangedTreasuryToOwnNfts) {
					validateTrue(currentTreasuryRel.getBalance() == 0, CURRENT_TREASURY_STILL_OWNS_NFTS);
				}
			}
		}
		if (op.hasAutoRenewAccount()) {
			newAutoRenew = accountStore.loadAccountOrFailWith(Id.fromGrpcAccount(op.getAutoRenewAccount()), INVALID_AUTORENEW_ACCOUNT);
			validateFalse(newAutoRenew.isSmartContract(), INVALID_AUTORENEW_ACCOUNT);
		}

		/* --- Do the business logic --- */
		final var tracker = new OwnershipTracker();
		token.update(op, newAutoRenew, newTreasury, validator);
		if (updatesTreasury && newTreasuryRel != null) {
			prepareNewTreasury(newTreasuryRel, token);
			transferTreasuryBalance(token, currentTreasuryRel, newTreasuryRel, tracker);
		}

		/* --- Persist changes --- */
		tokenStore.persistToken(token);
		if (updatesTreasury && newTreasuryRel != null) {
			if (token.getType().equals(TokenType.NON_FUNGIBLE_UNIQUE)) {
				accountStore.persistAccount(currentTreasuryRel.getAccount());
				accountStore.persistAccount(newTreasuryRel.getAccount());
			}
			tokenStore.persistTokenRelationships(List.of(newTreasuryRel, currentTreasuryRel));
			tokenStore.persistTrackers(tracker);
		}
	}

	private void validateExpiry(TokenUpdateTransactionBody op) {
		final var expirationIsInvalid = op.hasExpiry() && !validator.isValidExpiry(op.getExpiry());
		validateFalse(expirationIsInvalid, INVALID_EXPIRATION_TIME);
	}

	private void validateTokenIsMutable(final Token token, final TokenUpdateTransactionBody op) {
		final var isTokenImmutable = !token.hasAdminKey() && !affectsExpiryAtMost(op);
		validateFalse(isTokenImmutable, TOKEN_IS_IMMUTABLE);
	}

	private boolean affectsExpiryAtMost(TokenUpdateTransactionBody changes) {
		return !changes.hasAdminKey() &&
				!changes.hasKycKey() &&
				!changes.hasWipeKey() &&
				!changes.hasFreezeKey() &&
				!changes.hasSupplyKey() &&
				!changes.hasPauseKey() &&
				!changes.hasFeeScheduleKey() &&
				!changes.hasTreasury() &&
				!changes.hasAutoRenewAccount() &&
				changes.getSymbol().length() == 0 &&
				changes.getName().length() == 0 &&
				changes.getAutoRenewPeriod().getSeconds() == 0;
	}

	private void validateAssociationBetween(final Account newTreasury, final Token token) {
		final var associationDoesNotExist = !newTreasury.getAssociatedTokens().contains(token.getId());
		validateFalse(associationDoesNotExist, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
	}

	private void transferTreasuryBalance(final Token token,
										 final TokenRelationship currentTreasuryRel,
										 final TokenRelationship newTreasuryRel,
										 final OwnershipTracker tracker) {

		final var balance = currentTreasuryRel.getBalance();
		final var isEligibleTransfer = balance > 0L;

		if (isEligibleTransfer) {
			if (token.getType().equals(TokenType.FUNGIBLE_COMMON)) {
				doFungibleTransfer(currentTreasuryRel, newTreasuryRel, balance);
			} else {
				doNonFungibleTransfer(token.getId(), currentTreasuryRel, newTreasuryRel, tracker);
			}
		}
	}

	private void doFungibleTransfer(final TokenRelationship currentTreasuryRel,
									final TokenRelationship newTreasuryRel,
									final long balance) {
		currentTreasuryRel.setBalance(0L);
		newTreasuryRel.setBalance(balance);
	}

	private void doNonFungibleTransfer(final Id token,
									   final TokenRelationship fromTokenRelationship,
									   final TokenRelationship toTokenRelationship,
									   final OwnershipTracker tracker) {
		final var currentTreasury = fromTokenRelationship.getAccount();
		final var newTreasury = toTokenRelationship.getAccount();

		final var fromNftsOwned = currentTreasury.getOwnedNfts();
		final var toNftsOwned = newTreasury.getOwnedNfts();
		final var fromThisNftsOwned = fromTokenRelationship.getBalance();
		final var toThisNftsOwned = toTokenRelationship.getBalance();

		currentTreasury.setOwnedNfts(fromNftsOwned - fromThisNftsOwned);
		newTreasury.setOwnedNfts(toNftsOwned + fromThisNftsOwned);
		fromTokenRelationship.setBalance(0L);
		toTokenRelationship.setBalance(toThisNftsOwned + fromThisNftsOwned);

		tracker.add(token, new OwnershipTracker.Change(currentTreasury.getId(), newTreasury.getId(), -1));
	}

	private void prepareNewTreasury(final TokenRelationship newTreasuryRel, final Token token) {
		if (token.hasFreezeKey()) {
			newTreasuryRel.changeFrozenState(false);
		}
		if (token.hasKycKey()) {
			newTreasuryRel.changeKycState(true);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenUpdate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenUpdateTransactionBody op = txnBody.getTokenUpdate();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		var validity = !op.hasMemo() ? OK : validator.memoCheck(op.getMemo().getValue());
		if (validity != OK) {
			return validity;
		}


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
				op.hasFreezeKey(), op.getFreezeKey(),
				op.hasFeeScheduleKey(), op.getFeeScheduleKey(),
				op.hasPauseKey(), op.getPauseKey());

		return validity;
	}
}