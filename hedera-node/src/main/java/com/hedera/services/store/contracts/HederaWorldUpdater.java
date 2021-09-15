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

import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.repository.ContractDetails;
import com.hedera.services.store.contracts.repository.ServicesRepositoryRoot;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.AbstractWorldUpdater;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeSet;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

/**
 * {@link HederaWorldUpdater} must be renamed to HederaMutableWorldState and implement MutableWorldState to be equivalent to DefaultMutableWorld
 */
public class HederaWorldUpdater implements WorldUpdater {

	private final EntityIdSource ids;
	private final AccountStore accountStore;
	private final ServicesRepositoryRoot repositoryRoot;
	//TODO most probably we do not need those 5 properties here
	private final Map<Address, Bytes> updatedAccountCode = new HashMap<>();
	private final Map<Address, ContractDetails> updatedStorageTries = new HashMap<>();

	private final Map<Address, Bytes> provisionalCodeUpdates = new HashMap<>();
	private final Map<Address, EvmAccountImpl> provisionalAccountUpdates = new HashMap<>();
	private final Map<Id, com.hedera.services.store.models.Account> provisionalAccountCreations = new HashMap<>();

	@Inject
	public HederaWorldUpdater(
			final EntityIdSource ids,
			final AccountStore accountStore,
			final ServicesRepositoryRoot repositoryRoot
	) {
		this.ids = ids;
		this.accountStore = accountStore;
		this.repositoryRoot = repositoryRoot;
	}

	/**
	 * Allocates new Contract address based on the realm and shard of the sponsor
	 * IMPORTANT - The Id must be reclaimed if the MessageFrame reverts
	 *
	 * @param sponsor sponsor of the new contract
	 * @return newly generated contract {@link Address}
	 */
	public Address allocateNewContractAddress(Address sponsor) {
		Id newContractId = ids.newContractId(Id.fromEvmAddress(sponsor));
		provisionalAccountCreations.put(newContractId, new com.hedera.services.store.models.Account(newContractId));
		return newContractId.asEvmAddress();
	}

	//called by updater.commit()
	public void put(Address address, Wei balance) {
		provisionalAccountUpdates.put(address, new EvmAccountImpl(address, balance));
	}

	//called by updater.commit()
	public void putCode(Address address, Bytes code) {
		provisionalCodeUpdates.put(address, code);
	}

	public Bytes getCode(Address address) {
		if (provisionalCodeUpdates.containsKey(address)) {
			return provisionalCodeUpdates.get(address);
		} else {
			var codeBytes = repositoryRoot.getCode(address.toArray());
			return codeBytes == null ? Bytes.EMPTY : Bytes.of(codeBytes);
		}
	}

	//called by updater.commit()
	private void clearStorage(Address address) {
		//todo
	}

	@Override
	public WorldUpdater updater() {
		return new Updater(this);
	}

	//TODO MUST return OUR implementation of EvmAccount which is extension of {@link com.hedera.services.store.models.Account}
	@Override
	public Account get(Address address) {
		Id id = EntityIdUtils.idParsedFromEvmAddress(address.toArray());

		if (provisionalAccountCreations.containsKey(id)) {
			//TODO we should populate the balance, maybe code as-well
			return new HederaUpdateTrackingAccount(new EvmAccountImpl(address, Wei.of(0)));
		}

		if (accountStore.exists(id)) {
			com.hedera.services.store.models.Account hederaAccount = accountStore.loadAccount(id);

			var balance = hederaAccount.getBalance();
			if (hederaAccount.isSmartContract()) {
				var code = provisionalCodeUpdates.containsKey(address) ?
						provisionalCodeUpdates.get(address) : Bytes.of(repositoryRoot.getCode(address.toArray()));
				return new HederaUpdateTrackingAccount(new EvmAccountImpl(address, Wei.of(balance), code));
			}
			//TODO we must address nonces and mitigate all EVM related operations since Hedera does not have the concept of nonces
			return new HederaUpdateTrackingAccount(new EvmAccountImpl(address, Wei.of(balance)));
		}

		//TODO: test out when you want to send to a non-existing address in Hedera
		return null;
	}

	@Override
	public EvmAccount getAccount(final Address address) {
		//TODO if `get(addresss) is null it will throw since HederaUpdateTrackingAccount checks for != null
		return new HederaUpdateTrackingAccount(get(address));
	}

	@Override
	public EvmAccount createAccount(Address address, long nonce, Wei balance) {
		Id id = EntityIdUtils.idParsedFromEvmAddress(address.toArray());
		com.hedera.services.store.models.Account account = new com.hedera.services.store.models.Account(id);
		account.setBalance(balance.toLong());
		//TODO we should not persist the account in state yet. We must create it provisionally.
		accountStore.persistNew(account);
		return new HederaUpdateTrackingAccount(new EvmAccountImpl(address, balance));
	}

	@Override
	public void deleteAccount(Address address) {
		//todo
	}

	@Override
	public Collection<? extends Account> getTouchedAccounts() {
		return provisionalAccountUpdates.values();
	}

	@Override
	public Collection<Address> getDeletedAccountAddresses() {
		return null;
	}

	@Override
	public void revert() {
		//todo
	}

	@Override
	public void commit() {
		//todo sponsor, customizer? we ignore them with the accountstore
		provisionalAccountCreations.forEach((id, account) -> {
			validateTrue(!accountStore.exists(id), FAIL_INVALID);
			accountStore.persistNew(account);
		});

		provisionalAccountUpdates.forEach((address, evmAccount) -> {
			final var accountId = EntityIdUtils.idParsedFromEvmAddress(address.toArray());
			validateTrue(accountStore.exists(accountId), FAIL_INVALID);
			final var account = accountStore.loadAccount(accountId);
			//todo is this enough to update the account balance?
			account.setBalance(evmAccount.getBalance().toLong() - account.getBalance());
			accountStore.persistAccount(account);
		});

		/* Commit code updates for each updated address */
		provisionalCodeUpdates.forEach((address, code) -> {
			repositoryRoot.saveCode(address.toArray(), code.toArray());
		});

		repositoryRoot.flush();

		/* Clear any provisional changes */
		provisionalCodeUpdates.clear();
		provisionalAccountUpdates.clear();
		provisionalAccountCreations.clear();
	}

	@Override
	public Optional<WorldUpdater> parentUpdater() {
		return Optional.empty();
	}

	/**
	 * TODO we must extend {@link com.hedera.services.store.models.Account}
	 */
	public class WorldStateAccount implements Account {

		private final Address address;
		private final long nonce;
		private final Wei balance;

		private volatile ContractDetails storageTrie;

		public WorldStateAccount(final Address address, final long nonce, final Wei balance) {
			this.address = address;
			this.nonce = nonce;
			this.balance = balance;
		}

		private ContractDetails storageTrie() {
			final ContractDetails updatedTrie = updatedStorageTries.get(address);
			if (updatedTrie != null) {
				storageTrie = updatedTrie;
			}
			if (storageTrie == null) {
				storageTrie = repositoryRoot.getContractDetails(address.toArray());
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
			return nonce;
		}

		@Override
		public Wei getBalance() {
			return balance;
		}

		Hash getStorageRoot() {
			return Hash.EMPTY; // Not supported!
		}

		@Override
		public Bytes getCode() {
			final Bytes updatedCode = updatedAccountCode.get(address);
			if (updatedCode != null) {
				return updatedCode;
			}
			return Bytes.wrap(HederaWorldUpdater.this.getCode(address));
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
			return storageTrie().get(key);
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
					"storageRoot=" + getStorageRoot() + ", " +
					"codeHash=" + getCodeHash() + ", " +
					"}";
		}
	}

	/**
	 * This updater must extend `HederaAbstractWorldUpdater` instead of {@link AbstractWorldUpdater}
	 * - HederaAbstractWorldUpdater must use {@link HederaUpdateTrackingAccount} instead of {@link UpdateTrackingAccount}
	 * - HederaAbstractWorldUpdater must have new method: allocateNewContractAddress.
	 * The method will:
	 * - count the number of times the method is called
	 * - call the `HederaWorldView` to allocate new ID from {@link com.hedera.services.ledger.ids.SeqNoEntityIdSource}
	 * - HederaAbstractWorldUpdater must have new method: reclaimContractAddress
	 * The method will:
	 * - call the `HederaWorldView` to reclaim an ID from {@link com.hedera.services.ledger.ids.SeqNoEntityIdSource}
	 * - decrement the counter for `newContractAddressesAllocated`
	 * - StackedUpdater in HederaAbstractWorldUpdater must extend `HederaAbstractWorldUpdater` instead of {@link AbstractWorldUpdater}
	 * - HederaAbstractWorldUpdater on { UpdateTrackingAccount.reset} must clear the number of times `allocateNewContractAddress` was called
	 * - HederaAbstractWorldUpdater on { UpdateTrackingAccount.revert} must call the
	 * `HederaWorldView` and execute { com.hedera.services.ledger.ids.SeqNoEntityIdSource.reclaim} `newContractAddressesAllocated` times
	 */
	protected static class Updater
			extends AbstractWorldUpdater<HederaWorldUpdater, WorldStateAccount> {

		protected Updater(final HederaWorldUpdater world) {
			super(world);
		}

		@Override
		protected WorldStateAccount getForMutation(final Address address) {
			final HederaWorldUpdater wrapped = wrappedWorldView();
			Account acc = wrapped.get(address);
			return acc == null
					? null
					: wrapped.new WorldStateAccount(acc.getAddress(), acc.getNonce(), acc.getBalance());
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

			final HederaWorldUpdater wrapped = wrappedWorldView();

			for (final Address address : getDeletedAccounts()) {
				wrapped.deleteAccount(address);
				wrapped.updatedStorageTries.remove(address);
				wrapped.updatedAccountCode.remove(address);
			}

			for (final UpdateTrackingAccount<WorldStateAccount> updated : getUpdatedAccounts()) {
				final WorldStateAccount origin = updated.getWrappedAccount();

				// Save the code in storage ...
				if (updated.codeWasUpdated()) {
					wrapped.putCode(updated.getAddress(), updated.getCode());
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
							freshState ? wrapped.repositoryRoot.getContractDetails(origin.getAddress().toArray()) : origin.storageTrie();
					final TreeSet<Map.Entry<UInt256, UInt256>> entries =
							new TreeSet<>(Comparator.comparing(Map.Entry::getKey));
					entries.addAll(updatedStorage.entrySet());

					for (final Map.Entry<UInt256, UInt256> entry : entries) {
						final UInt256 value = entry.getValue();
						if (value.isZero()) {
							storageTrie.put(entry.getKey(), value);
						} else {
							storageTrie.put(entry.getKey(), value);
						}
					}
				}

				// Lastly, save the new account.
				//TODO we must not allow for arbitrary contract creation. If we do `get` for account and it
				// returns `null` we must halt the execution and revert
				wrapped.put(updated.getAddress(), updated.getBalance());
			}

			// Commit account state changes
			wrapped.commit();

			// Clear structures
			wrapped.updatedStorageTries.clear();
		}
	}
}

