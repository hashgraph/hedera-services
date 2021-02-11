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
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.legacy.core.jproto.JKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoCreate transaction,
 * and the conditions under which such logic is syntactically correct. (It is
 * possible that the <i>semantics</i> of the transaction will still be wrong;
 * for example, if the sponsor account can no longer afford to fund the
 * initial balance of the new account.)
 *
 * @author Michael Tinker
 */
public class CryptoCreateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(CryptoCreateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

	private final HederaLedger ledger;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;

	public CryptoCreateTransitionLogic(
			HederaLedger ledger,
			OptionValidator validator,
			TransactionContext txnCtx
	) {
		this.ledger = ledger;
		this.txnCtx = txnCtx;
		this.validator = validator;
	}

	@Override
	public void doStateTransition() {
		try {
			TransactionBody cryptoCreateTxn = txnCtx.accessor().getTxn();
			AccountID sponsor = cryptoCreateTxn.getTransactionID().getAccountID();

			CryptoCreateTransactionBody op = cryptoCreateTxn.getCryptoCreateAccount();
			long balance = op.getInitialBalance();
			AccountID created = ledger.create(sponsor, balance, asCustomizer(op));

			txnCtx.setCreated(created);
			txnCtx.setStatus(SUCCESS);
		} catch (InsufficientFundsException ife) {
			txnCtx.setStatus(INSUFFICIENT_PAYER_BALANCE);
		} catch (Exception e) {
			log.warn("Avoidable exception!", e);
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	private HederaAccountCustomizer asCustomizer(CryptoCreateTransactionBody op) {
		long autoRenewPeriod = op.getAutoRenewPeriod().getSeconds();
		long expiry = txnCtx.consensusTime().getEpochSecond() + autoRenewPeriod;

		/* Note that {@code this.validate(TransactionBody)} will have rejected any txn with an invalid key. */
		JKey key = asFcKeyUnchecked(op.getKey());
		HederaAccountCustomizer customizer = new HederaAccountCustomizer()
				.key(key)
				.memo(op.getMemo())
				.expiry(expiry)
				.autoRenewPeriod(autoRenewPeriod)
				.isReceiverSigRequired(op.getReceiverSigRequired());
		if (op.hasProxyAccountID()) {
			customizer.proxy(EntityId.ofNullableAccountId(op.getProxyAccountID()));
		}
		return customizer;
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoCreateAccount;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody cryptoCreateTxn) {
		CryptoCreateTransactionBody op = cryptoCreateTxn.getCryptoCreateAccount();

		if (!validator.isValidEntityMemo(op.getMemo())) {
			return MEMO_TOO_LONG;
		}
		if (!op.hasKey()) {
			return KEY_REQUIRED;
		}
		if (!validator.hasGoodEncoding(op.getKey())) {
			return BAD_ENCODING;
		}
		var fcKey = asFcKeyUnchecked(op.getKey());
		if (fcKey.isEmpty()) {
			return KEY_REQUIRED;
		}
		if (!fcKey.isValid()) {
			return BAD_ENCODING;
		}
		if (op.getInitialBalance() < 0L) {
			return INVALID_INITIAL_BALANCE;
		}
		if (!op.hasAutoRenewPeriod()) {
			return INVALID_RENEWAL_PERIOD;
		}
		if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
			return AUTORENEW_DURATION_NOT_IN_RANGE;
		}
		if (op.getSendRecordThreshold() < 0L) {
			return INVALID_SEND_RECORD_THRESHOLD;
		}
		if (op.getReceiveRecordThreshold() < 0L) {
			return INVALID_RECEIVE_RECORD_THRESHOLD;
		}

		return OK;
	}
}
