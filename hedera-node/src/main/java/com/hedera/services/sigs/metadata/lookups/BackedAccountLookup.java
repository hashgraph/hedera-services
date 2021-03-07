package com.hedera.services.sigs.metadata.lookups;

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

import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;

import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;

public class BackedAccountLookup implements AccountSigMetaLookup {
	private final BackingStore<AccountID, MerkleAccount> accounts;

	public BackedAccountLookup(BackingStore<AccountID, MerkleAccount> accounts) {
		this.accounts = accounts;
	}

	@Override
	public SafeLookupResult<AccountSigningMetadata> safeLookup(AccountID id) {
		return getResult(id, false);
	}

	@Override
	public SafeLookupResult<AccountSigningMetadata> softTouchLookup(AccountID id) {
		return getResult(id, true);
	}

	private SafeLookupResult<AccountSigningMetadata> getResult(AccountID id, boolean useUnsafe) {
		if (!accounts.contains(id)) {
			return SafeLookupResult.failure(MISSING_ACCOUNT);
		}
		var account = useUnsafe ? accounts.getUnsafeRef(id)  : accounts.getRef(id);
		return new SafeLookupResult<>(
				new AccountSigningMetadata(
						account.getKey(),
						account.isReceiverSigRequired()));
	}
}
