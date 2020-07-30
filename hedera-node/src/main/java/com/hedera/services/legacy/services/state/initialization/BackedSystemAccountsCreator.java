package com.hedera.services.legacy.services.state.initialization;

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

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.keys.LegacyEd25519KeyReader;
import com.hedera.services.ledger.accounts.BackingAccounts;
import com.hedera.services.state.initialization.SystemAccountsCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.AddressBook;
import com.swirlds.fcmap.FCMap;

import java.util.Set;
import java.util.stream.IntStream;

import static com.hedera.services.utils.EntityIdUtils.accountParsedFromString;
import static java.util.stream.Collectors.toSet;

public class BackedSystemAccountsCreator implements SystemAccountsCreator {
	private final HederaNumbers hederaNums;
	private final AccountNumbers accountNums;
	private final PropertySource properties;
	private final LegacyEd25519KeyReader b64KeyReader;

	private String hexedABytes;

	public BackedSystemAccountsCreator(
			HederaNumbers hederaNums,
			AccountNumbers accountNums,
			PropertySource properties,
			LegacyEd25519KeyReader b64KeyReader
	) {
		this.hederaNums = hederaNums;
		this.accountNums = accountNums;
		this.properties = properties;
		this.b64KeyReader = b64KeyReader;
	}

	@Override
	public void createSystemAccounts(FCMap<MerkleEntityId, MerkleAccount> accounts, AddressBook addressBook) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void ensureSystemAccounts(
			BackingAccounts<AccountID, MerkleAccount> accounts,
			AddressBook addressBook
	) {
		var nodeAccountNums = getNodeAccountNums(addressBook);
		System.out.println(nodeAccountNums);
		long N = properties.getIntProperty("bootstrap.accounts.init.numSystemAccounts");
		System.out.println(N);
		for (long num = 1; num <= N; num++) {
			if (accounts.contains(idWith(num).toAccountId())) {
				continue;
			}
			if (num == accountNums.treasury()) {
				getHexedABytes();
			} else if (nodeAccountNums.contains(num)) {
				throw new AssertionError("Not implemented");
			} else {
				throw new AssertionError("Not implemented");
			}
		}
	}

	private Set<Long> getNodeAccountNums(AddressBook addressBook) {
		return IntStream.range(0, addressBook.getSize())
				.mapToObj(addressBook::getAddress)
				.map(address -> accountParsedFromString(address.getMemo()).getAccountNum())
				.collect(toSet());
	}

	private String getHexedABytes() {
		if (hexedABytes == null) {
			hexedABytes = b64KeyReader.hexedABytesFrom(
					properties.getStringProperty("bootstrap.genesisB64Keystore.path"),
					properties.getStringProperty("bootstrap.genesisB64Keystore.keyName"));
		}
		return hexedABytes;
	}

	private MerkleEntityId idWith(long num) {
		return new MerkleEntityId(hederaNums.shard(), hederaNums.realm(), num);
	}
}
