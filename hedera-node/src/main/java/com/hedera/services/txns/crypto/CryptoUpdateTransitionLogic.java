package com.hedera.services.txns.crypto;

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
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.MissingAccountException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.legacy.core.jproto.JKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hedera.services.legacy.core.jproto.JKey.mapKey;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoUpdate transaction,
 * and the conditions under which such logic is syntactically correct. (It is
 * possible that the <i>semantics</i> of the transaction will still be wrong;
 * for example, if the target account was deleted before this transaction
 * reached consensus.)
 *
 * @author Michael Tinker
 */
public class CryptoUpdateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(CryptoUpdateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

	private final HederaLedger ledger;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;

	public CryptoUpdateTransitionLogic(
			HederaLedger ledger,
			OptionValidator validator,
			TransactionContext txnCtx
	) {
		this.ledger = ledger;
		this.validator = validator;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		try {
			CryptoUpdateTransactionBody op = txnCtx.accessor().getTxn().getCryptoUpdateAccount();
			AccountID target = op.getAccountIDToUpdate();

			ledger.customize(target, asCustomizer(op));
			txnCtx.setStatus(SUCCESS);
		} catch (MissingAccountException mae) {
			txnCtx.setStatus(INVALID_ACCOUNT_ID);
		} catch (DeletedAccountException aide) {
			txnCtx.setStatus(ACCOUNT_DELETED);
		} catch (Exception e) {
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	HederaAccountCustomizer asCustomizer(CryptoUpdateTransactionBody op) {
		HederaAccountCustomizer customizer = new HederaAccountCustomizer();

		if (op.hasKey()) {
			JKey key;
			try {
				key = mapKey(op.getKey());
			} catch (Exception syntaxViolation) {
				log.warn("Syntax violation in doStateTransition!", syntaxViolation);
				throw new IllegalArgumentException(syntaxViolation);
			}
			customizer.key(key);
		}
		if (op.hasSendRecordThresholdWrapper()) {
			customizer.fundsSentRecordThreshold(op.getSendRecordThresholdWrapper().getValue());
		} else if (op.getSendRecordThreshold() > 0) {
			customizer.fundsSentRecordThreshold(op.getSendRecordThreshold());
		}
		if (op.hasReceiveRecordThresholdWrapper()) {
			customizer.fundsReceivedRecordThreshold(op.getReceiveRecordThresholdWrapper().getValue());
		} else if (op.getReceiveRecordThreshold() > 0) {
			customizer.fundsReceivedRecordThreshold(op.getReceiveRecordThreshold());
		}
		if (op.hasExpirationTime()) {
			customizer.expiry(op.getExpirationTime().getSeconds());
		}
		if (op.hasProxyAccountID()) {
			customizer.proxy(EntityId.ofNullableAccountId(op.getProxyAccountID()));
		}
		if (op.hasReceiverSigRequiredWrapper()) {
			customizer.isReceiverSigRequired(op.getReceiverSigRequiredWrapper().getValue());
		} else if (op.getReceiverSigRequired()) {
			customizer.isReceiverSigRequired(true);
		}
		if (op.hasAutoRenewPeriod()) {
			customizer.autoRenewPeriod(op.getAutoRenewPeriod().getSeconds());
		}

		return customizer;
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoUpdateAccount;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	private ResponseCodeEnum validate(TransactionBody cryptoUpdateTxn) {
		CryptoUpdateTransactionBody op = cryptoUpdateTxn.getCryptoUpdateAccount();

		if (op.hasKey() && !validator.hasGoodEncoding(op.getKey())) {
			return BAD_ENCODING;
		}
		if (op.hasAutoRenewPeriod() && !validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
			return AUTORENEW_DURATION_NOT_IN_RANGE;
		}
		if (op.hasExpirationTime() && !validator.isValidExpiry(op.getExpirationTime())) {
			return INVALID_EXPIRATION_TIME;
		}

		return OK;
	}
}
