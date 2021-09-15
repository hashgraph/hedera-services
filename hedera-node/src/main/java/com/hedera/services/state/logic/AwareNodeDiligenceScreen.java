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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.diligence.DuplicateClassification;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

import static com.hedera.services.txns.diligence.DuplicateClassification.NODE_DUPLICATE;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;

@Singleton
public class AwareNodeDiligenceScreen {
	private static final Logger log = LogManager.getLogger(AwareNodeDiligenceScreen.class);

	final static String WRONG_NODE_LOG_TPL = "Node {} (member #{}) submitted a txn meant for node account {} :: {}";
	final static String MISSING_NODE_LOG_TPL = "Node {} (member #{}) submitted a txn w/ missing node account {} :: {}";

	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	@Inject
	public AwareNodeDiligenceScreen(
			OptionValidator validator,
			TransactionContext txnCtx,
			Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts
	) {
		this.txnCtx = txnCtx;
		this.validator = validator;
		this.accounts = accounts;
	}

	public boolean nodeIgnoredDueDiligence(DuplicateClassification duplicity) {
		var accessor = txnCtx.accessor();
		final var currentAccounts = accounts.get();
		var submittingAccount = txnCtx.submittingNodeAccount();
		var designatedAccount = accessor.getTxn().getNodeAccountID();
		boolean designatedNodeExists = currentAccounts.containsKey(fromAccountId(designatedAccount));
		if (!designatedNodeExists) {
			logAccountWarning(
					MISSING_NODE_LOG_TPL,
					submittingAccount,
					txnCtx.submittingSwirldsMember(),
					designatedAccount,
					accessor);
			txnCtx.setStatus(INVALID_NODE_ACCOUNT);
			return true;
		}

		var payerAccountId = accessor.getPayer();
		boolean payerAccountExists = currentAccounts.containsKey(fromAccountId(payerAccountId));

		if (!payerAccountExists) {
			txnCtx.setStatus(ACCOUNT_ID_DOES_NOT_EXIST);
			return true;
		}

		var payerAccountRef = currentAccounts.get(fromAccountId(payerAccountId));

		if (payerAccountRef.isDeleted()) {
			txnCtx.setStatus(PAYER_ACCOUNT_DELETED);
			return true;
		}

		if (!submittingAccount.equals(designatedAccount)) {
			logAccountWarning(
					WRONG_NODE_LOG_TPL,
					submittingAccount,
					txnCtx.submittingSwirldsMember(),
					designatedAccount,
					accessor);
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

		var memoValidity = validator.rawMemoCheck(accessor.getMemoUtf8Bytes(), accessor.memoHasZeroByte());
		if (memoValidity != OK) {
			txnCtx.setStatus(memoValidity);
			return true;
		}

		return false;
	}

	/**
	 * Logs account warnings
	 *
	 * @param message
	 * 		template for the log which includes each of the additional parameters
	 * @param submittingNodeAccount
	 * 		submitting node account for the transaction
	 * @param submittingMember
	 * 		submitting member
	 * @param relatedAccount
	 * 		related account as to which the warning applies to
	 * @param accessor
	 * 		transaction accessor
	 */
	private void logAccountWarning(
			String message,
			AccountID submittingNodeAccount,
			long submittingMember,
			AccountID relatedAccount,
			TxnAccessor accessor
	) {
		log.warn(message,
				readableId(submittingNodeAccount),
				submittingMember,
				readableId(relatedAccount),
				accessor.getSignedTxnWrapper());
	}

}
