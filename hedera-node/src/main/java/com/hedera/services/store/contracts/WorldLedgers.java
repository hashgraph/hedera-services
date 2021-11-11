package com.hedera.services.store.contracts;

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

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

import static com.hedera.services.ledger.TransactionalLedger.activeLedgerWrapping;

public class WorldLedgers {
	public static final WorldLedgers NULL_WORLD_LEDGERS =
			new WorldLedgers(null, null, null);

	final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
	final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger;

	public WorldLedgers(
			final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger,
			final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger
	) {
		this.tokenRelsLedger = tokenRelsLedger;
		this.accountsLedger = accountsLedger;
		this.nftsLedger = nftsLedger;
	}

	public void commit() {
		tokenRelsLedger.commit();
		accountsLedger.commit();
		nftsLedger.commit();
	}

	public void revert() {
		tokenRelsLedger.rollback();
		accountsLedger.rollback();
		nftsLedger.rollback();
	}

	public boolean areUsable() {
		return this != NULL_WORLD_LEDGERS;
	}

	public WorldLedgers wrapped() {
		if (this == NULL_WORLD_LEDGERS) {
			return NULL_WORLD_LEDGERS;
		}

		return new WorldLedgers(
				activeLedgerWrapping(tokenRelsLedger),
				activeLedgerWrapping(accountsLedger),
				activeLedgerWrapping(nftsLedger));
	}

	public TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRels() {
		return tokenRelsLedger;
	}

	public TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts() {
		return accountsLedger;
	}

	public TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nfts() {
		return nftsLedger;
	}
}
