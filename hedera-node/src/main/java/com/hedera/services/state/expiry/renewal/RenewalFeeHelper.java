package com.hedera.services.state.expiry.renewal;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;

public class RenewalFeeHelper {
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	private long totalFeesCharged = 0L;

	public RenewalFeeHelper(
			GlobalDynamicProperties dynamicProperties,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
	) {
		this.accounts = accounts;
		this.dynamicProperties = dynamicProperties;
	}

	public void beginChargingCycle() {
		totalFeesCharged = 0L;
	}

	public void recordCharged(long fee) {
		totalFeesCharged += fee;
	}

	public void endChargingCycle() {
		if (totalFeesCharged == 0L) {
			return;
		}

		final var currentAccounts = accounts.get();
		final var fundingAccountKey = fromAccountId(dynamicProperties.fundingAccount());
		final var mutableFundingAccount = currentAccounts.getForModify(fundingAccountKey);
		final long newBalance = mutableFundingAccount.getBalance() + totalFeesCharged;
		try {
			mutableFundingAccount.setBalance(newBalance);
		} catch (NegativeAccountBalanceException impossible) { }
		currentAccounts.replace(fundingAccountKey, mutableFundingAccount);
		totalFeesCharged = 0L;
	}

	long getTotalFeesCharged() {
		return totalFeesCharged;
	}
}
