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
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.MissingEntityException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

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

	private static final EnumSet<AccountProperty> EXPIRY_ONLY = EnumSet.of(AccountProperty.EXPIRY);

	private final HederaLedger ledger;
	private final OptionValidator validator;
	private final SigImpactHistorian sigImpactHistorian;
	private final TransactionContext txnCtx;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public CryptoUpdateTransitionLogic(
			final HederaLedger ledger,
			final OptionValidator validator,
			final SigImpactHistorian sigImpactHistorian,
			final TransactionContext txnCtx,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.ledger = ledger;
		this.validator = validator;
		this.txnCtx = txnCtx;
		this.sigImpactHistorian = sigImpactHistorian;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void doStateTransition() {
		try {
			final var op = txnCtx.accessor().getTxn().getCryptoUpdateAccount();
			final var target = op.getAccountIDToUpdate();
			final var customizer = asCustomizer(op);

			if (op.hasExpirationTime() && !validator.isValidExpiry(op.getExpirationTime())) {
				txnCtx.setStatus(INVALID_EXPIRATION_TIME);
				return;
			}
			final var validity = sanityCheck(target, customizer);
			if (validity != OK) {
				txnCtx.setStatus(validity);
				return;
			}

			ledger.customize(target, customizer);
			sigImpactHistorian.markEntityChanged(target.getAccountNum());
			txnCtx.setStatus(SUCCESS);
		} catch (MissingEntityException mae) {
			txnCtx.setStatus(INVALID_ACCOUNT_ID);
		} catch (DeletedAccountException aide) {
			txnCtx.setStatus(ACCOUNT_DELETED);
		} catch (Exception e) {
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxnWrapper(), e);
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	private ResponseCodeEnum sanityCheck(AccountID target, HederaAccountCustomizer customizer) {
		if (!ledger.exists(target) || ledger.isSmartContract(target)) {
			return INVALID_ACCOUNT_ID;
		}

		final var changes = customizer.getChanges();
		final var keyChanges = customizer.getChanges().keySet();

		if (ledger.isDetached(target) && !keyChanges.equals(EXPIRY_ONLY)) {
			return ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
		}

		if (keyChanges.contains(AccountProperty.EXPIRY)) {
			final long newExpiry = (long) changes.get(AccountProperty.EXPIRY);
			if (newExpiry < ledger.expiry(target)) {
				return EXPIRATION_REDUCTION_NOT_ALLOWED;
			}
		}

		if (keyChanges.contains(AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS)) {
			final long newMax = (int) changes.get(AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS);
			if (newMax < ledger.alreadyUsedAutomaticAssociations(target)) {
				return EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
			}
			if (dynamicProperties.areTokenAssociationsLimited() && newMax > dynamicProperties.maxTokensPerAccount()) {
				return REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
			}
		}

		return OK;
	}

	private HederaAccountCustomizer asCustomizer(CryptoUpdateTransactionBody op) {
		HederaAccountCustomizer customizer = new HederaAccountCustomizer();

		if (op.hasKey()) {
			/* Note that {@code this.validate(TransactionBody)} will have rejected any txn with an invalid key. */
			var fcKey = asFcKeyUnchecked(op.getKey());
			customizer.key(fcKey);
		}
		if (op.hasExpirationTime()) {
			customizer.expiry(op.getExpirationTime().getSeconds());
		}
		if (op.hasProxyAccountID()) {
			customizer.proxy(EntityId.fromGrpcAccountId(op.getProxyAccountID()));
		}
		if (op.hasReceiverSigRequiredWrapper()) {
			customizer.isReceiverSigRequired(op.getReceiverSigRequiredWrapper().getValue());
		} else if (op.getReceiverSigRequired()) {
			customizer.isReceiverSigRequired(true);
		}
		if (op.hasAutoRenewPeriod()) {
			customizer.autoRenewPeriod(op.getAutoRenewPeriod().getSeconds());
		}
		if (op.hasMemo()) {
			customizer.memo(op.getMemo().getValue());
		}
		if (op.hasMaxAutomaticTokenAssociations()) {
			customizer.maxAutomaticAssociations(op.getMaxAutomaticTokenAssociations().getValue());
		}
		return customizer;
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoUpdateAccount;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
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
