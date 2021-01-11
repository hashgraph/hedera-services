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
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.exceptions.MissingAccountException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.validation.TokenListChecks.checkTokenTransfers;
import static com.hedera.services.txns.validation.TransferListChecks.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoTransfer transaction,
 * and the conditions under which such logic is syntactically correct. (It is
 * possible that the <i>semantics</i> of the transaction will still be wrong;
 * for example, if one of the accounts involved no longer has the necessary
 * funds available after consensus.)
 *
 * @author Michael Tinker
 */
public class CryptoTransferTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(CryptoTransferTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

	private final HederaLedger ledger;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;

	public CryptoTransferTransitionLogic(
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
			var op = txnCtx.accessor().getTxn().getCryptoTransfer();
			var outcome = ledger.doAtomicTransfers(op);
			txnCtx.setStatus((outcome == OK) ? SUCCESS : outcome);
		} catch (Exception e) {
			log.warn("Avoidable exception!", e);
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	public static ResponseCodeEnum tryTransfers(HederaLedger ledger, TransferList transfers) {
		if (!hasOnlyCryptoAccounts(ledger, transfers)) {
			return INVALID_ACCOUNT_ID;
		}
		try {
			ledger.doTransfers(transfers);
		} catch (MissingAccountException mae) {
			return ACCOUNT_ID_DOES_NOT_EXIST;
		} catch (DeletedAccountException aide) {
			return ACCOUNT_DELETED;
		} catch (InsufficientFundsException ife) {
			return INSUFFICIENT_ACCOUNT_BALANCE;
		}
		return OK;
	}

	static boolean hasOnlyCryptoAccounts(HederaLedger ledger, TransferList transfers) {
		for (AccountAmount aa : transfers.getAccountAmountsList()) {
			var id = aa.getAccountID();
			if (!ledger.exists(id) || ledger.isSmartContract(id)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoTransfer;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	private ResponseCodeEnum validate(TransactionBody txn) {
		var op = txn.getCryptoTransfer();

		var validity = basicSyntaxChecks(op.getTransfers(), validator);
		if (validity != OK) {
			return validity;
		}

		validity = validator.isAcceptableTokenTransfersLength(op.getTokenTransfersList());
		if (validity != OK) {
			return validity;
		}

		return checkTokenTransfers(op.getTokenTransfersList());
	}

	public static ResponseCodeEnum basicSyntaxChecks(TransferList transfers, OptionValidator validator) {
		if (hasRepeatedAccount(transfers)) {
			return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
		} else if (!isNetZeroAdjustment(transfers)) {
			return INVALID_ACCOUNT_AMOUNTS;
		} else if (!validator.isAcceptableTransfersLength(transfers)) {
			return TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
		} else {
			return OK;
		}
	}
}
