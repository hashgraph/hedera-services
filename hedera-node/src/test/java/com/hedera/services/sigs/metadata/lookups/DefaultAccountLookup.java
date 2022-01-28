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

import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.sigs.metadata.SafeLookupResult;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.FcAllowanceId;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;

import javax.annotation.Nullable;
import java.util.function.Supplier;

import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;
import static com.hedera.services.utils.EntityIdUtils.isAlias;
import static com.hedera.services.utils.EntityNum.fromAccountId;

/**
 * Trivial account signing metadata lookup backed by a {@code FCMap<MapKey, MapValue>}.
 */
public class DefaultAccountLookup implements AccountSigMetaLookup {
	private final AliasManager aliasManager;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

	public DefaultAccountLookup(
			final AliasManager aliasManager,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts
	) {
		this.aliasManager = aliasManager;
		this.accounts = accounts;
	}

	@Override
	public SafeLookupResult<AccountSigningMetadata> safeLookup(final AccountID id) {
		return lookupByNumber(fromAccountId(id));
	}

	@Override
	public SafeLookupResult<AccountSigningMetadata> aliasableSafeLookup(final AccountID idOrAlias) {
		if (isAlias(idOrAlias)) {
			final var explicitId = aliasManager.lookupIdBy(idOrAlias.getAlias());
			return (explicitId == EntityNum.MISSING_NUM)
					? SafeLookupResult.failure(MISSING_ACCOUNT)
					: lookupByNumber(explicitId);
		} else {
			return lookupByNumber(fromAccountId(idOrAlias));
		}
	}

	@Override
	public boolean allowanceGrantLookupFor(final AccountID payerID, final AccountID ownerID, final @Nullable TokenID tokenID) {
		final var payerNum = isAlias(payerID) ? aliasManager.lookupIdBy(payerID.getAlias()) : fromAccountId(payerID);
		final var ownerNum = isAlias(ownerID) ? aliasManager.lookupIdBy(ownerID.getAlias()) : fromAccountId(ownerID);

		if (tokenID != null) {
			final var tokenAllowanceMap = accounts.get().get(ownerNum).getTokenAllowances();
			return tokenAllowanceMap.containsKey(FcAllowanceId.from(EntityNum.fromTokenId(tokenID), payerNum));
		} else {
			final var hbarAllowanceMap = accounts.get().get(ownerNum).getCryptoAllowances();
			return hbarAllowanceMap.containsKey(payerNum);
		}
	}

	private SafeLookupResult<AccountSigningMetadata> lookupByNumber(final EntityNum id) {
		var account = accounts.get().get(id);
		if (account == null) {
			return SafeLookupResult.failure(MISSING_ACCOUNT);
		} else {
			return new SafeLookupResult<>(
					new AccountSigningMetadata(
							account.getAccountKey(), account.isReceiverSigRequired()));
		}
	}
}
