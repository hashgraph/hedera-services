package com.hedera.services.txns.crypto;

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
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoUpdate transaction,
 * and the conditions under which such logic has valid semantics. (It is
 * possible that the transaction will still resolve to a status other than
 * success; for example if the target account has been deleted when the
 * update is handled.)
 */
@Singleton
public class CryptoUpdateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(CryptoUpdateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final AccountStore accountStore;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final TransactionRecordService transactionRecordService;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public CryptoUpdateTransitionLogic(
			AccountStore accountStore,
			OptionValidator validator,
			TransactionContext txnCtx,
			TransactionRecordService transactionRecordService,
			GlobalDynamicProperties dynamicProperties
	) {
		this.accountStore = accountStore;
		this.validator = validator;
		this.txnCtx = txnCtx;
		this.transactionRecordService = transactionRecordService;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getCryptoUpdateAccount();

		/* --- Load the model objects --- */
		final var accountId = Id.fromGrpcAccount(op.getAccountIDToUpdate());
		final var account = accountStore.loadPossiblyDetachedAccount(accountId);

		if (account.isDetached()) {
			validateTrue(affectsExpiryAtMost(op), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
		}
		if (op.hasExpirationTime()) {
			validateTrue(validator.isValidExpiry(op.getExpirationTime()), INVALID_EXPIRATION_TIME);
			validateTrue(op.getExpirationTime().getSeconds() > account.getExpiry(), EXPIRATION_REDUCTION_NOT_ALLOWED);
		}
		if (op.hasMaxAutomaticTokenAssociations()) {
			validateTrue(op.getMaxAutomaticTokenAssociations().getValue() >= account.getAlreadyUsedAutomaticAssociations(), EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT);
			validateTrue(op.getMaxAutomaticTokenAssociations().getValue() <= dynamicProperties.maxTokensPerAccount(), REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);
		}

		/* --- Do the business logic --- */
		account.updateFromGrpc(
				optional(op.hasKey(), op.getKey()),
				optional(op.hasMemo(), op.getMemo().getValue()),
				optional(op.hasAutoRenewPeriod(), op.getAutoRenewPeriod().getSeconds()),
				optional(op.hasExpirationTime(), op.getExpirationTime().getSeconds()),
				optional(op.hasProxyAccountID(), op.getProxyAccountID()),
				optional(op.hasReceiverSigRequiredWrapper(), op.getReceiverSigRequiredWrapper().getValue()),
				optional(op.hasMaxAutomaticTokenAssociations(), op.getMaxAutomaticTokenAssociations().getValue())
		);

		/* --- Persist the models --- */
		accountStore.persistAccount(account);
		/* --- Externalise the transaction effects in the record stream */
		transactionRecordService.includeChangesToAccount(account);
	}

	private Optional optional(boolean isPresent, Object value) {
		return isPresent ? Optional.of(value) : Optional.empty();
	}

	private boolean affectsExpiryAtMost(CryptoUpdateTransactionBody changes) {
		return !changes.hasKey() &&
				!changes.hasMemo() &&
				!changes.hasAutoRenewPeriod() &&
				!changes.hasProxyAccountID() &&
				!changes.hasReceiverSigRequiredWrapper() &&
				!changes.hasMaxAutomaticTokenAssociations() &&
				changes.hasExpirationTime();
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoUpdateAccount;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	private ResponseCodeEnum validate(TransactionBody cryptoUpdateTxn) {
		CryptoUpdateTransactionBody op = cryptoUpdateTxn.getCryptoUpdateAccount();

		var memoValidity = !op.hasMemo() ? OK : validator.memoCheck(op.getMemo().getValue());
		if (memoValidity != OK) {
			return memoValidity;
		}

		if (op.hasKey()) {
			try {
				JKey fcKey = JKey.mapKey(op.getKey());
				/* Note that an empty key is never valid. */
				if (!fcKey.isValid()) {
					return BAD_ENCODING;
				}
			} catch (DecoderException e) {
				return BAD_ENCODING;
			}
		}

		if (op.hasAutoRenewPeriod() && !validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
			return AUTORENEW_DURATION_NOT_IN_RANGE;
		}
		return OK;
	}
}
