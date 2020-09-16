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

import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;
import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;

/**
 * Trivial account signing metadata lookup backed by a {@code FCMap<MapKey, MapValue>}.
 *
 * @author Michael Tinker
 */
public class DefaultFCMapAccountLookup implements AccountSigMetaLookup {
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	public DefaultFCMapAccountLookup(Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts) {
		this.accounts = accounts;
	}

	/**
	 * Returns metadata for the given account's signing activity; e.g., whether
	 * the account must sign transactions in which it receives cryptocurrency.
	 *
	 * @param id
	 * 		the account to recover signing metadata for.
	 * @return the desired metadata.
	 * @throws InvalidAccountIDException
	 * 		if the backing {@code FCMap} has no matching entry.
	 */
	@Override
	public AccountSigningMetadata lookup(AccountID id) throws Exception {
		MerkleAccount account = accounts.get().get(fromAccountId(id));
		if (account == null) {
			throw new InvalidAccountIDException("Invalid account!", id);
		}
		return new AccountSigningMetadata(account.getKey(), account.isReceiverSigRequired());
	}

	@Override
	public SafeLookupResult<AccountSigningMetadata> safeLookup(AccountID id) {
		var account = accounts.get().get(fromAccountId(id));
		return (account == null)
				? SafeLookupResult.failure(MISSING_ACCOUNT)
				: new SafeLookupResult<>(
				new AccountSigningMetadata(
						account.getKey(),
						account.isReceiverSigRequired()));
	}
}
