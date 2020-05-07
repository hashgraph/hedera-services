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

import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hederahashgraph.api.proto.java.AccountAmount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hedera.services.legacy.core.MapKey.getMapKey;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.legacy.core.MapKey;
import com.swirlds.fcmap.FCMap;

import java.util.List;
import java.util.Optional;

public class QueryFeeCheck {
	private final FCMap<MapKey, HederaAccount> accounts;

	public QueryFeeCheck(FCMap<MapKey, HederaAccount> accounts) {
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
		if (Optional.ofNullable(transfers).map(List::size).orElse(0 ) == 0) {
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
		var key = getMapKey(id);
		long amount = adjustment.getAmount();

		if (amount == Long.MIN_VALUE) {
			return INVALID_ACCOUNT_AMOUNTS;
		}

		if (amount < 0) {
			var balanceStatus = Optional.ofNullable(accounts.get(key))
					.filter(account -> account.getBalance() >= Math.abs(amount))
					.map(ignore -> OK)
					.orElse(INSUFFICIENT_PAYER_BALANCE);
			if (balanceStatus != OK) {
				return balanceStatus;
			}
		} else {
			if (!accounts.containsKey(key)) {
				return ACCOUNT_ID_DOES_NOT_EXIST;
			}
		}

		return OK;
	}
}
