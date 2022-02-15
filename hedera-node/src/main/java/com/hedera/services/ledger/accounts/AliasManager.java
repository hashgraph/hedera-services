package com.hedera.services.ledger.accounts;

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

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static com.hedera.services.utils.EntityIdUtils.isAlias;
import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.services.utils.MiscUtils.forEach;

/**
 * Handles a map with all the accounts that are auto-created. The map will be re-built on restart, reconnect.
 * Entries from the map are removed when the entity expires
 */
@Singleton
public class AliasManager extends AbstractContractAliases implements ContractAliases {
	private static final Logger log = LogManager.getLogger(AliasManager.class);

	private static final String NON_TRANSACTIONAL_MSG = "Base alias manager does not buffer changes";

	private Map<ByteString, EntityNum> aliases;

	@Inject
	public AliasManager() {
		this.aliases = new HashMap<>();
	}

	@Override
	public void revert() {
		throw new UnsupportedOperationException(NON_TRANSACTIONAL_MSG);
	}

	@Override
	public void filterPendingChanges(Predicate<Address> filter) {
		throw new UnsupportedOperationException(NON_TRANSACTIONAL_MSG);
	}

	@Override
	public void commit(final @Nullable SigImpactHistorian observer) {
		throw new UnsupportedOperationException(NON_TRANSACTIONAL_MSG);
	}

	@Override
	public void link(final Address alias, final Address address) {
		link(ByteString.copyFrom(alias.toArrayUnsafe()), EntityNum.fromEvmAddress(address));
	}

	@Override
	public void unlink(final Address alias) {
		unlink(ByteString.copyFrom(alias.toArrayUnsafe()));
	}

	@Override
	public Address resolveForEvm(final Address addressOrAlias) {
		if (isMirror(addressOrAlias)) {
			return addressOrAlias;
		}
		final var aliasKey = ByteString.copyFrom(addressOrAlias.toArrayUnsafe());
		final var contractNum = aliases.get(aliasKey);
		return (contractNum == null) ? null : contractNum.toEvmAddress();
	}

	@Override
	public boolean isInUse(final Address address) {
		return aliases.containsKey(ByteString.copyFrom(address.toArrayUnsafe()));
	}

	public void link(final ByteString alias, final EntityNum num) {
		aliases.put(alias, num);
	}

	public void unlink(final ByteString alias) {
		aliases.remove(alias);
	}

	/**
	 * From given MerkleMap of accounts, populate the auto accounts creations map. Iterate through
	 * each account in accountsMap and add an entry to autoAccountsMap if {@code alias} exists on the account.
	 *
	 * @param accounts
	 * 		the current accounts
	 */
	public void rebuildAliasesMap(final MerkleMap<EntityNum, MerkleAccount> accounts) {
		aliases.clear();
		forEach(accounts, (k, v) -> {
			if (!v.getAlias().isEmpty()) {
				aliases.put(v.getAlias(), k);
			}
		});
		log.info("Rebuild complete : No.of accounts with aliases {} ", aliases.size());
	}

	/**
	 * Removes an entry from the autoAccountsMap when an entity is expired and deleted from the ledger.
	 *
	 * @param expiredId
	 * 		entity id that is expired
	 * @param accounts
	 * 		current accounts map
	 * @return whether the alias was forgotten
	 */
	public boolean forgetAliasIfPresent(final EntityNum expiredId, final MerkleMap<EntityNum, MerkleAccount> accounts) {
		final var alias = accounts.get(expiredId).getAlias();
		if (!alias.isEmpty()) {
			aliases.remove(alias);
			return true;
		}
		return false;
	}

	/**
	 * Returns if there is an account linked the given alias.
	 *
	 * @param alias
	 * 		the alias of interest
	 * @return whether there is a linked account
	 */
	public boolean contains(final ByteString alias) {
		return aliases.containsKey(alias);
	}

	/**
	 * Returns the entityNum for the given alias
	 *
	 * @param alias
	 * 		alias of the accountId
	 * @return EntityNum mapped to the given alias.
	 */
	public EntityNum lookupIdBy(final ByteString alias) {
		return aliases.getOrDefault(alias, MISSING_NUM);
	}

	/* --- Only used by unit tests --- */
	void setAliases(final Map<ByteString, EntityNum> aliases) {
		this.aliases = aliases;
	}

	Map<ByteString, EntityNum> getAliases() {
		return aliases;
	}

	public EntityNum unaliased(final AccountID grpcId) {
		if (isAlias(grpcId)) {
			return lookupIdBy(grpcId.getAlias());
		}
		return EntityNum.fromAccountId(grpcId);
	}
}
