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
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.TokenTypesMapper;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hedera.services.txns.validation.TokenListChecks.suppliesCheck;
import static com.hedera.services.txns.validation.TokenListChecks.supplyTypeCheck;
import static com.hedera.services.txns.validation.TokenListChecks.typeCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static java.util.stream.Collectors.toList;

/**
 * Provides the state transition for token creation.
 */
@Singleton
public class TokenCreateTransitionLogic implements TransitionLogic {
	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final AccountStore accountStore;
	private final EntityIdSource ids;
	private final OptionValidator validator;
	private final TypedTokenStore typedTokenStore;
	private final TransactionContext txnCtx;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public TokenCreateTransitionLogic(
			OptionValidator validator,
			TypedTokenStore typedTokenStore,
			AccountStore accountStore,
			TransactionContext txnCtx,
			GlobalDynamicProperties dynamicProperties,
			EntityIdSource ids
	) {
		this.validator = validator;
		this.txnCtx = txnCtx;
		this.dynamicProperties = dynamicProperties;
		this.ids = ids;
		this.accountStore = accountStore;
		this.typedTokenStore = typedTokenStore;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenCreation();
		validateExpiry(op);

		/* --- Load existing model objects --- */
		final var treasuryId = Id.fromGrpcAccount(op.getTreasury());
		final var treasury = accountStore.loadAccountOrFailWith(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
		Account autoRenew = null;
		if (op.hasAutoRenewAccount()) {
			final var autoRenewId = Id.fromGrpcAccount(op.getAutoRenewAccount());
			autoRenew = accountStore.loadAccountOrFailWith(autoRenewId, INVALID_AUTORENEW_ACCOUNT);
		}

		/* --- Create, update, and validate model objects  --- */
		final var tokenId = Id.fromGrpcToken(ids.newTokenId(txnCtx.activePayer()));
		final var maxCustomFees = dynamicProperties.maxCustomFeesAllowed();
		validateTrue(op.getCustomFeesCount() <= maxCustomFees, CUSTOM_FEES_LIST_TOO_LONG);
		final var now = txnCtx.consensusTime().getEpochSecond();
		final var provisionalToken = Token.fromGrpcOpAndMeta(tokenId, op, treasury, autoRenew, now);
		final var newRels = newRelsFor(provisionalToken, treasury);
		if (op.getInitialSupply() > 0) {
			provisionalToken.mint(newRels.get(0), op.getInitialSupply(), true);
		}

		/* --- Persist all new and updated models --- */
		typedTokenStore.persistNew(provisionalToken);
		typedTokenStore.persistTokenRelationships(newRels);
		newRels.forEach(rel -> accountStore.persistAccount(rel.getAccount()));

		/* --- Record activity in the transaction context --- */
		txnCtx.setNewTokenAssociations(newRels.stream().map(TokenRelationship::asAutoAssociation).collect(toList()));
	}

	private List<TokenRelationship> newRelsFor(Token provisionalToken, Account treasury) {
		final int maxTokensPerAccount = dynamicProperties.maxTokensPerAccount();
		final Set<Id> associatedSoFar = new HashSet<>();
		final List<TokenRelationship> newRelations = new ArrayList<>();

		associateGiven(maxTokensPerAccount, provisionalToken, treasury, associatedSoFar, newRelations);

		for (final var customFee : provisionalToken.getCustomFees()) {
//			if (fee.shouldCollectorBeAutoAssociated(created.getId())) {
//				final var collector = fee.getCollector();
//				if (!associatedAccounts.contains(collector.getId())) {
//					final var collectorRelation = created.newEnabledRelationship(collector);
//					if (!collector.getAssociatedTokens().contains(created.getId())) {
//						collector.associateWith(List.of(created), dynamicProperties.maxTokensPerAccount(), false);
//					}
//					relations.add(collectorRelation);
//				}
//			}
		}

		return newRelations;
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenCreation;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	@Override
	public void reclaimCreatedIds() {
		ids.reclaimProvisionalIds();
	}

	@Override
	public void resetCreatedIds() {
		ids.resetProvisionalIds();
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenCreateTransactionBody op = txnBody.getTokenCreation();

		if (TokenTypesMapper.mapToDomain(op.getTokenType()) == TokenType.NON_FUNGIBLE_UNIQUE && !dynamicProperties.areNftsEnabled()) {
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

	private void associateGiven(
			final int maxTokensPerAccount,
			final Token provisionalToken,
			final Account account,
			final Set<Id> associatedSoFar,
			final List<TokenRelationship> newRelations
	)  {
		final var accountId = account.getId();
		if (associatedSoFar.contains(accountId)) {
			return;
		}

		final var newRel = provisionalToken.newEnabledRelationship(account);
		account.associateWith(List.of(provisionalToken), maxTokensPerAccount, false);
		newRelations.add(newRel);
		associatedSoFar.add(accountId);
	}

	private void validateExpiry(TokenCreateTransactionBody op) {
		final var hasValidExpiry = op.hasExpiry() && validator.isValidExpiry(op.getExpiry());
		validateTrue(hasValidExpiry, INVALID_EXPIRATION_TIME);
	}
}