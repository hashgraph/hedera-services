package com.hedera.services.state.logic;

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
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.legacy.services.state.AwareProcessLogic;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.diligence.DuplicateClassification;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.txns.diligence.DuplicateClassification.NODE_DUPLICATE;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class AwareNodeDiligenceScreen {
	static Logger log = LogManager.getLogger(AwareNodeDiligenceScreen.class);

	final static String WRONG_NODE_LOG_TPL = "Node {} (member #{}) submitted a txn meant for node account {} :: {}";
	final static String MISSING_NODE_LOG_TPL = "Node {} (member #{}) submitted a txn w/ missing node account {} :: {}";

	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final BackingStore<AccountID, MerkleAccount> backingAccounts;

	public AwareNodeDiligenceScreen(
			OptionValidator validator,
			TransactionContext txnCtx,
			BackingStore<AccountID, MerkleAccount> backingAccounts
	) {
		this.txnCtx = txnCtx;
		this.validator = validator;
		this.backingAccounts = backingAccounts;
	}

	public boolean nodeIgnoredDueDiligence(DuplicateClassification duplicity) {
		var accessor = txnCtx.accessor();

		var submittingAccount = txnCtx.submittingNodeAccount();
		var designatedAccount = accessor.getTxn().getNodeAccountID();

		boolean designatedNodeExists = backingAccounts.contains(designatedAccount);
		if (!designatedNodeExists) {
			warnOfMissing(submittingAccount, txnCtx.submittingSwirldsMember(), designatedAccount, accessor);
			txnCtx.setStatus(INVALID_NODE_ACCOUNT);
			return true;
		}

		if (!submittingAccount.equals(designatedAccount)) {
			warnOfMismatched(submittingAccount, txnCtx.submittingSwirldsMember(), designatedAccount, accessor);
			txnCtx.setStatus(INVALID_NODE_ACCOUNT);
			return true;
		}

		if (!txnCtx.isPayerSigKnownActive()) {
			txnCtx.setStatus(INVALID_PAYER_SIGNATURE);
			return true;
		}

		if (duplicity == NODE_DUPLICATE) {
			txnCtx.setStatus(DUPLICATE_TRANSACTION);
			return true;
		}

		long txnDuration = accessor.getTxn().getTransactionValidDuration().getSeconds();
		if (!validator.isValidTxnDuration(txnDuration)) {
			txnCtx.setStatus(INVALID_TRANSACTION_DURATION);
			return true;
		}

		var cronStatus = validator.chronologyStatus(accessor, txnCtx.consensusTime());
		if (cronStatus != OK) {
			txnCtx.setStatus(cronStatus);
			return true;
		}

		var memoValidity = validator.memoCheck(accessor.getTxn().getMemo());
		if (memoValidity != OK) {
			txnCtx.setStatus(memoValidity);
			return true;
		}

		return false;
	}

	private void warnOfMissing(
			AccountID submittingNodeAccount,
			long submittingMember,
			AccountID designatedNodeAccount,
			TxnAccessor accessor
	) {
		log.warn(MISSING_NODE_LOG_TPL,
				readableId(submittingNodeAccount),
				submittingMember,
				readableId(designatedNodeAccount),
				accessor.getSignedTxn4Log());
	}

	private void warnOfMismatched(
			AccountID submittingNodeAccount,
			long submittingMember,
			AccountID designatedNodeAccount,
			TxnAccessor accessor
	) {
		log.warn(WRONG_NODE_LOG_TPL,
				readableId(submittingNodeAccount),
				submittingMember,
				readableId(designatedNodeAccount),
				accessor.getSignedTxn4Log());
	}
}
