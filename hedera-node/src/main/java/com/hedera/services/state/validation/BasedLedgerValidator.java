package com.hedera.services.state.validation;

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

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;

public class BasedLedgerValidator implements LedgerValidator {
	private final HederaNumbers hederaNums;
	private final PropertySource properties;

	public BasedLedgerValidator(HederaNumbers hederaNums, PropertySource properties) {
		this.hederaNums = hederaNums;
		this.properties = properties;
	}

	@Override
	public void assertIdsAreValid(FCMap<MerkleEntityId, MerkleAccount> accounts) {
		long maxAccountNum = properties.getLongProperty("ledger.maxAccountNum");

		for (MerkleEntityId id : accounts.keySet()) {
			if (id.getRealm() != hederaNums.realm()) {
				throw new IllegalStateException(String.format("Invalid realm in account %s", id.toAbbrevString()));
			}
			if (id.getShard() != hederaNums.shard()) {
				throw new IllegalStateException(String.format("Invalid shard in account %s", id.toAbbrevString()));
			}
			if (id.getNum() < 1 || id.getNum() > maxAccountNum) {
				throw new IllegalStateException(String.format("Invalid num in account %s", id.toAbbrevString()));
			}
		}
	}

	@Override
	public boolean hasExpectedTotalBalance(FCMap<MerkleEntityId, MerkleAccount> accounts) {
		long actualFloat = accounts.values().stream().mapToLong(MerkleAccount::getBalance).sum();
		long expectedFloat = properties.getLongProperty("ledger.totalTinyBarFloat");

		return expectedFloat == actualFloat;
	}
}
