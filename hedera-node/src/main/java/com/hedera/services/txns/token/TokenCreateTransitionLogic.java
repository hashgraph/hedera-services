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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.token.process.Creation;
import com.hedera.services.txns.token.process.NewRels;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.TokenTypesMapper;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hedera.services.txns.validation.TokenListChecks.suppliesCheck;
import static com.hedera.services.txns.validation.TokenListChecks.supplyTypeCheck;
import static com.hedera.services.txns.validation.TokenListChecks.typeCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;

/**
 * Provides the state transition for token creation.
 */
@Singleton
public class TokenCreateTransitionLogic implements TransitionLogic {
	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	static final Creation.NewRelsListing RELS_LISTING = NewRels::listFrom;
	static final Creation.TokenModelFactory MODEL_FACTORY = Token::fromGrpcOpAndMeta;
	private Creation.CreationFactory creationFactory = Creation::new;

	private final AccountStore accountStore;
	private final EntityIdSource ids;
	private final OptionValidator validator;
	private final TypedTokenStore tokenStore;
	private final SigImpactHistorian sigImpactHistorian;
	private final TransactionContext txnCtx;
	private final GlobalDynamicProperties dynamicProperties;
	private final SideEffectsTracker sideEffectsTracker;

	@Inject
	public TokenCreateTransitionLogic(
			final OptionValidator validator,
			final TypedTokenStore tokenStore,
			final AccountStore accountStore,
			final TransactionContext txnCtx,
			final GlobalDynamicProperties dynamicProperties,
			final EntityIdSource ids,
			final SigImpactHistorian sigImpactHistorian,
			final SideEffectsTracker sideEffectsTracker
	) {
		this.validator = validator;
		this.txnCtx = txnCtx;
		this.dynamicProperties = dynamicProperties;
		this.ids = ids;
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
		this.sigImpactHistorian = sigImpactHistorian;
		this.sideEffectsTracker = sideEffectsTracker;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenCreation();
		final var creation = creationFactory.processFrom(accountStore, tokenStore, dynamicProperties, op);

		/* --- Load existing model objects --- */
		creation.loadModelsWith(txnCtx.activePayer(), ids, validator);

		/* --- Create, update, and validate model objects  --- */
		final var now = txnCtx.consensusTime().getEpochSecond();
		creation.doProvisionallyWith(now, MODEL_FACTORY, RELS_LISTING);

		/* --- Persist all new and updated models --- */
		creation.persist();

		/* --- Record activity in the transaction context --- */
		creation.newAssociations().forEach(sideEffectsTracker::trackExplicitAutoAssociation);
		sigImpactHistorian.markEntityChanged(creation.newTokenId().num());
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

		final var domainType = TokenTypesMapper.mapToDomain(op.getTokenType());
		if (domainType == TokenType.NON_FUNGIBLE_UNIQUE && !dynamicProperties.areNftsEnabled()) {
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
				op.hasFeeScheduleKey(), op.getFeeScheduleKey(),
				op.hasPauseKey(), op.getPauseKey());
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

	/* --- Only used by unit tests --- */
	public void setCreationFactory(Creation.CreationFactory creationFactory) {
		this.creationFactory = creationFactory;
	}
}