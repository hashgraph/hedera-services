package com.hedera.services.sigs.metadata.lookups;

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

import com.hedera.services.ledger.accounts.BackingAccounts;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;

import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;

public class BackedAccountLookup implements AccountSigMetaLookup {
	private final BackingAccounts<AccountID, MerkleAccount> accounts;

	public BackedAccountLookup(BackingAccounts<AccountID, MerkleAccount> accounts) {
		this.accounts = accounts;
	}

	@Override
	public AccountSigningMetadata lookup(AccountID id) throws Exception {
		if (accounts.contains(id)) {
			var account = accounts.getRef(id);
			return new AccountSigningMetadata(account.getKey(), account.isReceiverSigRequired());
		} else {
			throw new InvalidAccountIDException("Invalid account!", id);
		}
	}

	@Override
	public SafeLookupResult<AccountSigningMetadata> safeLookup(AccountID id) {
		if (!accounts.contains(id)) {
			return SafeLookupResult.failure(MISSING_ACCOUNT);
		}
		var account = accounts.getRef(id);
		return new SafeLookupResult<>(
				new AccountSigningMetadata(
						account.getKey(),
						account.isReceiverSigRequired()));
	}
}
