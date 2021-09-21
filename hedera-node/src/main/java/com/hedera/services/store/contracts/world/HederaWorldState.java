package com.hedera.services.store.contracts.world;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.ethereum.db.ContractDetails;
import org.ethereum.db.ServicesRepositoryRoot;
import org.ethereum.vm.DataWord;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.worldstate.AbstractWorldUpdater;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

@Singleton
public class HederaWorldState implements HederaMutableWorldState {

	private final EntityIdSource ids;
	private final AccountStore accountStore;
	private final ServicesRepositoryRoot repositoryRoot;

	private final List<Address> provisionalAccountDeletes = new ArrayList<>();

	private final Map<Address, ContractDetails> provisionalStorageUpdates = new HashMap<>();
	private final Map<Address, Bytes> provisionalCodeUpdates = new HashMap<>();
	private final Map<Address, WorldStateAccount> provisionalAccountUpdates = new HashMap<>();

	@Inject
	public HederaWorldState(
			final EntityIdSource ids,
			final AccountStore accountStore,
			final ServicesRepositoryRoot repositoryRoot
	) {
		this.ids = ids;
		this.accountStore = accountStore;
		this.repositoryRoot = repositoryRoot;
	}

	private void clearStorage(Address address) {
		//todo implement me
	}

	@Override
	public void persist() {
		provisionalAccountDeletes.forEach(address -> {
			final var accountId = EntityIdUtils.idParsedFromEvmAddress(address.toArray());
			validateTrue(accountStore.exists(accountId), FAIL_INVALID);
			final var account = accountStore.loadAccount(accountId);
			account.setDeleted(true);
			accountStore.persistAccount(account);
		});

		//todo implement light account balance update in accountStore and use it
		provisionalAccountUpdates.forEach((address, worldStateAccount) -> {
			final var accountId = EntityIdUtils.idParsedFromEvmAddress(address.toArray());
			if (accountStore.exists(accountId)) {
				final var account = accountStore.loadAccount(accountId);
				//todo are we setting the correct balance here?
				account.setBalance(worldStateAccount.balance.toLong());
				accountStore.persistAccount(account);
			} else {
				com.hedera.services.store.models.Account toPersist = new com.hedera.services.store.models.Account(
						accountId);
				toPersist.setSmartContract(true);
				toPersist.setMemo(worldStateAccount.getMemo());
				toPersist.setKey(worldStateAccount.getKey());
				toPersist.setProxy(worldStateAccount.getProxyAccount());
				toPersist.setBalance(worldStateAccount.getBalance().toLong());
				// TODO expiry, autoRenewSecs
				accountStore.persistNew(toPersist);
			}
		});

		/* Commit code updates for each updated address */
		provisionalCodeUpdates.forEach((address, code) -> {
			repositoryRoot.saveCode(address.toArray(), code.toArray());
		});

		repositoryRoot.flush();

		/* Clear any provisional changes */
		provisionalCodeUpdates.clear();
		provisionalAccountUpdates.clear();
		provisionalAccountDeletes.clear();
		provisionalStorageUpdates.clear();
	}

	@Override
	public Id newContractId(Address sponsor) {
		return ids.newContractId(Id.fromEvmAddress(sponsor));
	}

	@Override
	public void reclaimContractId() {
		ids.reclaimLastId();
	}

	@Override
	public Updater updater() {
		return new Updater(this);
	}

	@Override
	public Hash rootHash() {
		return Hash.EMPTY;
	}

	@Override
	public Hash frontierRootHash() {
		return rootHash();
	}

	@Override
	public Stream<StreamableAccount> streamAccounts(Bytes32 startKeyHash, int limit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public WorldStateAccount get(Address address) {
		Id id = Id.fromEvmAddress(address);
		if (accountStore.exists(id)) {
			// TODO introduce a more lightweight method for getting the balance
			final var account = accountStore.loadAccount(id);
			return new WorldStateAccount(address, Wei.of(account.getBalance()));
		}
		return null;
	}

	public void addPropertiesFor(Address address, String memo, JKey key, Id proxyAccount) {
		WorldStateAccount account = this.provisionalAccountUpdates.get(address);
		account.setMemo(memo);
		account.setKey(key);
		account.setProxyAccount(proxyAccount);

		this.provisionalAccountUpdates.put(address, account);
	}

	public class WorldStateAccount implements Account {

		private volatile ContractDetails storageTrie;

		private final Address address;
		private final Wei balance;
		private String memo;
		private JKey key;
		private Id proxyAccount;

		public WorldStateAccount(final Address address, final Wei balance) {
			this.address = address;
			this.balance = balance;
		}

		private ContractDetails storageTrie() {
			final ContractDetails updatedTrie = provisionalStorageUpdates.get(getAddress());
			if (updatedTrie != null) {
				storageTrie = updatedTrie;
			}
			if (storageTrie == null) {
				storageTrie = repositoryRoot.getContractDetails(getAddress().toArray());
			}
			return storageTrie;
		}

		@Override
		public Address getAddress() {
			return address;
		}

		@Override
		public Hash getAddressHash() {
			return Hash.EMPTY; // Not supported!
		}

		@Override
		public long getNonce() {
			return 0;
		}

		@Override
		public Wei getBalance() {
			return balance;
		}

		@Override
		public Bytes getCode() {
			final Bytes updatedCode = provisionalCodeUpdates.get(getAddress());
			if (updatedCode != null) {
				return updatedCode;
			}
			var codeBytes = repositoryRoot.getCode(getAddress().toArray());
			return codeBytes == null ? Bytes.EMPTY : Bytes.of(codeBytes);
		}

		@Override
		public boolean hasCode() {
			return !getCode().isEmpty();
		}

		@Override
		public Hash getCodeHash() {
			return Hash.EMPTY; // Not supported!
		}

		@Override
		public UInt256 getStorageValue(final UInt256 key) {
			DataWord dwValue = storageTrie().get(DWUtil.fromUInt256(key));
			return dwValue == null ? UInt256.ZERO : DWUtil.fromDataWord(dwValue);
		}

		@Override
		public UInt256 getOriginalStorageValue(final UInt256 key) {
			return getStorageValue(key);
		}

		@Override
		public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
				final Bytes32 startKeyHash, final int limit) {
			throw new UnsupportedOperationException("Stream storage entries not supported");
		}

		@Override
		public String toString() {
			return "AccountState" + "{" +
					"address=" + getAddress() + ", " +
					"nonce=" + getNonce() + ", " +
					"balance=" + getBalance() + ", " +
					"codeHash=" + getCodeHash() + ", " +
					"}";
		}

		public void setMemo(String memo) {
			this.memo = memo;
		}

		public void setKey(JKey key) {
			this.key = key;
		}

		public void setProxyAccount(Id proxyAccount) {
			this.proxyAccount = proxyAccount;
		}

		public String getMemo() {
			return memo;
		}

		public JKey getKey() {
			return key;
		}

		public Id getProxyAccount() {
			return proxyAccount;
		}
	}

	/**
	 * This updater must extend `HederaAbstractWorldUpdater` instead of {@link AbstractWorldUpdater}
	 * - HederaAbstractWorldUpdater must use HederaUpdateTrackingAccount instead of {@link UpdateTrackingAccount}
	 * - HederaAbstractWorldUpdater must have new method: allocateNewContractAddress.
	 * The method will:
	 * - count the number of times the method is called
	 * - call the `HederaWorldView` to allocate new ID from {@link com.hedera.services.ledger.ids.SeqNoEntityIdSource}
	 * - HederaAbstractWorldUpdater must have new method: reclaimContractAddress
	 * The method will:
	 * - call the `HederaWorldView` to reclaim an ID from {@link com.hedera.services.ledger.ids.SeqNoEntityIdSource}
	 * - decrement the counter for `newContractAddressesAllocated`
	 * - StackedUpdater in HederaAbstractWorldUpdater must extend `HederaAbstractWorldUpdater` instead of {@link
	 * AbstractWorldUpdater}
	 * - HederaAbstractWorldUpdater on { UpdateTrackingAccount.reset} must clear the number of times
	 * `allocateNewContractAddress` was called
	 * - HederaAbstractWorldUpdater on { UpdateTrackingAccount.revert} must call the
	 * `HederaWorldView` and execute { com.hedera.services.ledger.ids.SeqNoEntityIdSource.reclaim}
	 * `newContractAddressesAllocated` times
	 */
	public static class Updater extends AbstractWorldUpdater<HederaWorldState, WorldStateAccount> {

		protected Updater(final HederaWorldState world) {
			super(world);
		}

		@Override
		protected WorldStateAccount getForMutation(final Address address) {
			final HederaWorldState wrapped = wrappedWorldView();
			return wrapped.get(address);
		}

		@Override
		public Collection<? extends Account> getTouchedAccounts() {
			return new ArrayList<>(getUpdatedAccounts());
		}

		@Override
		public Collection<Address> getDeletedAccountAddresses() {
			return new ArrayList<>(getDeletedAccounts());
		}

		@Override
		public void revert() {
			getDeletedAccounts().clear();
			getUpdatedAccounts().clear();
		}

		@Override
		public void commit() {
			final HederaWorldState wrapped = wrappedWorldView();
			wrapped.provisionalAccountDeletes.addAll(getDeletedAccountAddresses());

			for (final UpdateTrackingAccount<WorldStateAccount> updated : getUpdatedAccounts()) {
				final WorldStateAccount origin = updated.getWrappedAccount();

				// Save the code in storage ...
				if (updated.codeWasUpdated()) {
					wrapped.provisionalCodeUpdates.put(updated.getAddress(), updated.getCode());
				}
				// ...and storage in the account trie first.
				final boolean freshState = origin == null || updated.getStorageWasCleared();
				if (freshState) {
					wrapped.clearStorage(updated.getAddress());
				}

				final Map<UInt256, UInt256> updatedStorage = updated.getUpdatedStorage();
				if (!updatedStorage.isEmpty()) {
					// Apply any storage updates
					final ContractDetails storageTrie =
							freshState ? wrapped.repositoryRoot.getContractDetails(
									updated.getAddress().toArray()) : origin.storageTrie();
					final TreeSet<Map.Entry<UInt256, UInt256>> entries =
							new TreeSet<>(Comparator.comparing(Map.Entry::getKey));
					entries.addAll(updatedStorage.entrySet());

					for (final Map.Entry<UInt256, UInt256> entry : entries) {
						final UInt256 value = entry.getValue();
						if (value.isZero()) {
							storageTrie.put(DWUtil.fromUInt256(entry.getKey()), DataWord.ZERO);
						} else {
							storageTrie.put(DWUtil.fromUInt256(entry.getKey()), DWUtil.fromUInt256(value));
						}
					}

					wrapped.provisionalStorageUpdates.put(updated.getAddress(), storageTrie);
				}

				if (updated.getWrappedAccount() != null) {
					wrapped.provisionalAccountUpdates.put(updated.getAddress(), updated.getWrappedAccount());
				} else {
					wrapped.provisionalAccountUpdates.put(updated.getAddress(),
							wrapped.new WorldStateAccount(updated.getAddress(), updated.getBalance()));
				}
			}
		}
	}
}
