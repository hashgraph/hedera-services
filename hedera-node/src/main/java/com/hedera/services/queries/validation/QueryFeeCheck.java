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

	public ResponseCodeEnum nodePaymentValidity(List<AccountAmount> transfers, long queryFee, AccountID node) {
		var plausibility = transfersPlausibility(transfers);
		if (plausibility != OK) {
			return plausibility;
		}

		long netPayment = -1 * transfers.stream()
				.mapToLong(AccountAmount::getAmount)
				.filter(amount -> amount < 0)
				.sum();
		if (netPayment < queryFee) {
			return INSUFFICIENT_TX_FEE;
		}
		// number of beneficiaries in query transfer transaction can be greater than one.
		// validate if node gets the required query payment
		if (transfers.stream().noneMatch(adj -> adj.getAmount() > 0 && adj.getAccountID().equals(node))) {
			return INVALID_RECEIVING_NODE_ACCOUNT;
		}
		if (transfers.stream().anyMatch(adj -> adj.getAccountID().equals(node) && adj.getAmount() < queryFee)) {
			return INSUFFICIENT_TX_FEE;
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
	 * Validates query payment transfer transaction before reaching consensus.
	 * Validate each payer has enough balance that is needed for transfer.
	 * If one of the payer for query is also paying transactionFee validate the payer has balance to pay both
	 *
	 * @param txn
	 * @return
	 */
	public ResponseCodeEnum validateQueryPaymentTransfers(TransactionBody txn) {
		AccountID transactionPayer = txn.getTransactionID().getAccountID();
		TransferList transferList = txn.getCryptoTransfer().getTransfers();
		List<AccountAmount> transfers = transferList.getAccountAmountsList();
		long transactionFee = txn.getTransactionFee();

		for (AccountAmount accountAmount : transfers) {
			var id = accountAmount.getAccountID();
			long amount = accountAmount.getAmount();

			if (amount < 0) {
				amount = -1 * amount;
				if (id.equals(transactionPayer)) {
					try {
						amount = Math.addExact(amount, transactionFee);
					} catch (ArithmeticException e) {
						return INSUFFICIENT_PAYER_BALANCE;
					}
				}
				if (!hasPayerEnoughBalance(id, amount)) {
					return INSUFFICIENT_PAYER_BALANCE;
				}
			}
		}
		return OK;
	}

	/**
	 * Check if each payer in transfer list of query payment transfer transaction has enough balance for transfer
	 *
	 * @param payerAccount
	 * @param amount
	 * @return
	 */
	private boolean hasPayerEnoughBalance(AccountID payerAccount, long amount) {
		Long payerAccountBalance = Optional.ofNullable(accounts.get().get(fromAccountId(payerAccount)))
				.map(MerkleAccount::getBalance)
				.orElse(null);
		if (payerAccountBalance < amount) {
			return false;
		}
		return true;
	}
}
