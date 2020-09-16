package com.hedera.services.queries.validation;

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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountAmount;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.fcmap.FCMap;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class QueryFeeCheck {
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	public QueryFeeCheck(Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts) {
		this.accounts = accounts;
	}

	public ResponseCodeEnum nodePaymentValidity(List<AccountAmount> transfers, long fee, AccountID node) {
		var plausibility = transfersPlausibility(transfers);
		if (plausibility != OK) {
			return plausibility;
		}

		long netPayment = -1 * transfers.stream()
				.mapToLong(AccountAmount::getAmount)
				.filter(amount -> amount < 0)
				.sum();
		if (netPayment < fee) {
			return INSUFFICIENT_TX_FEE;
		}

		var numBeneficiaries = transfers.stream()
				.filter(adjustment -> adjustment.getAmount() > 0)
				.count();
		if (numBeneficiaries != 1) {
			return INVALID_RECEIVING_NODE_ACCOUNT;
		}
		if (transfers.stream().noneMatch(adj -> adj.getAmount() == netPayment && adj.getAccountID().equals(node))) {
			return INVALID_RECEIVING_NODE_ACCOUNT;
		}

		return OK;
	}

	ResponseCodeEnum transfersPlausibility(List<AccountAmount> transfers) {
		if (Optional.ofNullable(transfers).map(List::size).orElse(0) == 0) {
			return INVALID_ACCOUNT_AMOUNTS;
		}

		var basicPlausibility = transfers
				.stream()
				.map(this::adjustmentPlausibility)
				.filter(status -> status != OK)
				.findFirst()
				.orElse(OK);
		if (basicPlausibility != OK) {
			return basicPlausibility;
		}

		try {
			long net = transfers.stream()
					.mapToLong(AccountAmount::getAmount)
					.reduce(0L, Math::addExact);
			return (net == 0) ? OK : INVALID_ACCOUNT_AMOUNTS;
		} catch (ArithmeticException ignore) {
			return INVALID_ACCOUNT_AMOUNTS;
		}
	}

	ResponseCodeEnum adjustmentPlausibility(AccountAmount adjustment) {
		var id = adjustment.getAccountID();
		var key = MerkleEntityId.fromAccountId(id);
		long amount = adjustment.getAmount();

		if (amount == Long.MIN_VALUE) {
			return INVALID_ACCOUNT_AMOUNTS;
		}

		if (amount < 0) {
			var balanceStatus = Optional.ofNullable(accounts.get().get(key))
					.filter(account -> account.getBalance() >= Math.abs(amount))
					.map(ignore -> OK)
					.orElse(INSUFFICIENT_PAYER_BALANCE);
			if (balanceStatus != OK) {
				return balanceStatus;
			}
		} else {
			if (!accounts.get().containsKey(key)) {
				return ACCOUNT_ID_DOES_NOT_EXIST;
			}
		}

		return OK;
	}

	/**
	 * Validates query payment transaction before reaching consensus.
	 * Query payment transaction should have only two account amounts in transfer list,
	 * where payer account is transferring to node account
	 *
	 * @param txn
	 * @return
	 */
	public ResponseCodeEnum validateQueryPaymentTransaction(TransactionBody txn) {
		AccountID payerAccount = txn.getTransactionID().getAccountID();
		TransferList transferList = txn.getCryptoTransfer().getTransfers();
		List<AccountAmount> transfers = transferList.getAccountAmountsList();
		long suppliedFee = txn.getTransactionFee();

		if (Optional.ofNullable(transfers).map(List::size).orElse(0) != 2) {
			return INVALID_QUERY_PAYMENT_ACCOUNT_AMOUNTS;
		}

		ResponseCodeEnum response = checkNodeAndPayerAccounts(transfers,
				payerAccount, txn.getNodeAccountID());
		if (response != OK) {
			return response;
		}

		response = checkPayerBalance(payerAccount, transfers, suppliedFee);
		if (response != OK) {
			return response;
		}
		return OK;
	}

	/**
	 * Check if payer can afford transaction fee and transfer amount
	 *
	 * @param payerAccount
	 * @param transfers
	 * @param suppliedFee
	 * @return
	 */
	private ResponseCodeEnum checkPayerBalance(AccountID payerAccount, List<AccountAmount> transfers,
			long suppliedFee) {
		Long payerAccountBalance = Optional.ofNullable(accounts.get().get(fromAccountId(payerAccount)))
				.map(MerkleAccount::getBalance)
				.orElse(null);
		long transferAmount = -1 * transfers.stream()
				.mapToLong(AccountAmount::getAmount)
				.filter(amount -> amount < 0)
				.sum();
		try {
			if (payerAccountBalance < Math.addExact(transferAmount, suppliedFee)) {
				return INSUFFICIENT_PAYER_BALANCE;
			}
		} catch (ArithmeticException e) {
			return INSUFFICIENT_PAYER_BALANCE;
		}
		return OK;
	}

	/**
	 * Check payer account and node accounts in transfer list of query payment transaction
	 *
	 * @param transfers
	 * @param payer
	 * @param node
	 * @return
	 */
	ResponseCodeEnum checkNodeAndPayerAccounts(List<AccountAmount> transfers, AccountID payer, AccountID node) {
		for (AccountAmount accountAmount : transfers) {
			var id = accountAmount.getAccountID();
			long amount = accountAmount.getAmount();

			if (amount < 0) {
				if (!id.equals(payer)) {
					return INVALID_PAYER_ACCOUNT_ID;
				}
			} else {
				if (!id.equals(node)) {
					return INVALID_RECEIVING_NODE_ACCOUNT;
				}
			}
		}
		return OK;
	}

}
