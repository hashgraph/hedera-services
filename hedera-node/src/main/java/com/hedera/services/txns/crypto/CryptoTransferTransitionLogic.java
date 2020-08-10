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
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;
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
			var transfers = txnCtx.accessor().getTxn().getCryptoTransfer().getTransfers();

			if (!validator.hasOnlyCryptoAccounts(transfers)) {
				txnCtx.setStatus(INVALID_ACCOUNT_ID);
				return;
			}

			ledger.doTransfers(transfers);
			txnCtx.setStatus(SUCCESS);
		} catch (MissingAccountException mae) {
			txnCtx.setStatus(ACCOUNT_ID_DOES_NOT_EXIST);
		} catch (DeletedAccountException aide) {
			txnCtx.setStatus(ACCOUNT_DELETED);
		} catch (InsufficientFundsException ife) {
			txnCtx.setStatus(INSUFFICIENT_ACCOUNT_BALANCE);
		} catch (Exception e) {
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoTransfer;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	private ResponseCodeEnum validate(TransactionBody cryptoTransferTxn) {
		CryptoTransferTransactionBody op = cryptoTransferTxn.getCryptoTransfer();
		TransferList accountAmounts = op.getTransfers();
		ResponseCodeEnum responseCode;

		if (hasRepeatedAccount(accountAmounts)) {
			responseCode = ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
		} else if (!isNetZeroAdjustment(accountAmounts)) {
			responseCode = INVALID_ACCOUNT_AMOUNTS;
		} else if (!validator.isAcceptableLength(accountAmounts)) {
			responseCode = TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
		} else {
			responseCode = OK;
		}
		return responseCode;
	}
}
