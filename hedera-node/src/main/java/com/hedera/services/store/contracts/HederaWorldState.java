package com.hedera.services.store.contracts;

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

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
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
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

@Singleton
public class HederaWorldState implements HederaMutableWorldState {

	private final EntityIdSource ids;
	private final HederaLedger ledger;
	private final ServicesRepositoryRoot repositoryRoot;

	private final List<Address> provisionalAccountDeletes = new ArrayList<>();
	private final Map<Address, Bytes> provisionalCodeUpdates = new HashMap<>();
	private final Map<Address, WorldStateAccount> provisionalAccountUpdates = new HashMap<>();
	private final Map<Address, Address> sponsorMap = new LinkedHashMap<>();

	@Inject
	public HederaWorldState(
			final EntityIdSource ids,
			final HederaLedger ledger,
			final ServicesRepositoryRoot repositoryRoot
	) {
		this.ids = ids;
		this.ledger = ledger;
		this.repositoryRoot = repositoryRoot;
	}


	@Override
	public List<ContractID> persist() {
		provisionalAccountDeletes.forEach(address -> {
			final var accountId = accountParsedFromSolidityAddress(address.toArray());
			// account may have been created and deleted within the same transaction.
			if (ledger.exists(accountId)) {
				var merkleAccount = ledger.get(accountId);
				// selfdestruct adjusts balances to zero
				ledger.adjustBalance(accountId, -merkleAccount.getBalance());

				validateTrue(!ledger.isDeleted(accountId), FAIL_INVALID);
				HederaAccountCustomizer customizer = new HederaAccountCustomizer().isDeleted(true);
				ledger.customize(accountId, customizer);
			}
		});

		final var createdContracts = new ArrayList<ContractID>();

		// copy over sponsor account info for CREATE operations
		sponsorMap.forEach((contract, sponsorAddress) -> {
			if (!provisionalAccountUpdates.containsKey(contract)) return;

			AccountID sponsorAccount = EntityIdUtils.accountParsedFromSolidityAddress(sponsorAddress.toArrayUnsafe());

			WorldStateAccount provisionalAccount = provisionalAccountUpdates.get(contract);
			if (ledger.exists(sponsorAccount)) {
				MerkleAccount sponsorMerkleAccount = ledger.get(sponsorAccount);
				provisionalAccount.setProxyAccount(sponsorMerkleAccount.getProxy());
				provisionalAccount.setKey(sponsorMerkleAccount.getAccountKey());
				provisionalAccount.setMemo(sponsorMerkleAccount.getMemo());
				provisionalAccount.setAutoRenew(sponsorMerkleAccount.getAutoRenewSecs());
				provisionalAccount.setExpiry(sponsorMerkleAccount.getExpiry());
			} else if (provisionalAccountUpdates.containsKey(sponsorAddress)) {
				WorldStateAccount sponsorProvisionalAccount = provisionalAccountUpdates.get(sponsorAddress);
				provisionalAccount.setProxyAccount(sponsorProvisionalAccount.getProxyAccount());
				provisionalAccount.setKey(sponsorProvisionalAccount.getKey());
				provisionalAccount.setMemo(sponsorProvisionalAccount.getMemo());
				provisionalAccount.setAutoRenew(sponsorProvisionalAccount.getAutoRenew());
				provisionalAccount.setExpiry(sponsorProvisionalAccount.getExpiry());
			}
		});

		provisionalAccountUpdates.forEach((address, worldStateAccount) -> {
			final var accountId = accountParsedFromSolidityAddress(address.toArray());
			if (ledger.exists(accountId)) {
				long oldBalance = ledger.getBalance(accountId);
				long newBalance = worldStateAccount.getBalance().toLong();
				long adjustment = newBalance - oldBalance;
				ledger.adjustBalance(accountId, adjustment);
			} else {
//				var key = new JContractIDKey(worldStateAccount.getProxyAccount().toGrpcContractId()); // TODO: might be used for factory contracts
				HederaAccountCustomizer customizer = new HederaAccountCustomizer()
						.key(worldStateAccount.getKey())
						.memo("")
						.proxy(worldStateAccount.getProxyAccount())
						.expiry(worldStateAccount.getExpiry())
						.autoRenewPeriod(worldStateAccount.getAutoRenew())
						.isSmartContract(true);
				if (provisionalAccountDeletes.contains(address)) {
					customizer.isDeleted(true);
				}

				long balance = worldStateAccount.getBalance().toLong();
				ledger.spawn(accountId, balance, customizer);
				createdContracts.add(EntityIdUtils.asContract(accountId));
			}
		});

		/* Commit code updates for each updated address */
		provisionalCodeUpdates.forEach((address, code) -> repositoryRoot.saveCode(address.toArray(), code.toArray()));

		repositoryRoot.flush();

		/* Clear any provisional changes */
		provisionalCodeUpdates.clear();
		provisionalAccountUpdates.clear();
		provisionalAccountDeletes.clear();
		sponsorMap.clear();

		return createdContracts;
	}

	@Override
	public Address newContractAddress(Address sponsor) {
		final var newContractId =
				ids.newContractId(EntityIdUtils.accountParsedFromSolidityAddress(sponsor.toArrayUnsafe()));
		return Address.wrap(Bytes.wrap(EntityIdUtils.asSolidityAddress(newContractId)));
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
		AccountID id = accountParsedFromSolidityAddress(address.toArray());
		if (!ledger.exists(id) || ledger.isDetached(id) || ledger.isDeleted(id)) {
			return null;
		}

		final long expiry = ledger.expiry(id);
		final EntityId proxy = ledger.proxy(id);
		final long balance = ledger.getBalance(id);
		final long autoRenewPeriod = ledger.autoRenewPeriod(id);
		return new WorldStateAccount(address, Wei.of(balance), expiry, autoRenewPeriod, proxy);
	}

	public void addPropertiesFor(Address address, String memo, JKey key, Id proxyAccount) {
		WorldStateAccount account = this.provisionalAccountUpdates.get(address);
		account.setMemo(memo);
		account.setKey(key);
		account.setProxyAccount(proxyAccount.asEntityId());

		this.provisionalAccountUpdates.put(address, account);
	}

	public class WorldStateAccount implements Account {

		private final Wei balance;
		private final Address address;

		private JKey key;
		private String memo;
		private EntityId proxyAccount;
		private long expiry;
		private long autoRenew;

		public WorldStateAccount(final Address address, final Wei balance) {
			this.address = address;
			this.balance = balance;
		}

		public WorldStateAccount(final Address address, final Wei balance, long expiry, long autoRenew,
				EntityId proxyAccount) {
			this.expiry = expiry;
			this.address = address;
			this.balance = balance;
			this.autoRenew = autoRenew;
			this.proxyAccount = proxyAccount;
		}

		private ContractDetails storageTrie() {
			return repositoryRoot.getContractDetails(getAddress().toArray());
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
			return Hash.hash(this.getCode());
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

		public String getMemo() {
			return memo;
		}

		public void setMemo(String memo) {
			this.memo = memo;
		}

		public JKey getKey() {
			return key;
		}

		public void setKey(JKey key) {
			this.key = key;
		}

		public EntityId getProxyAccount() {
			return proxyAccount;
		}

		public void setProxyAccount(EntityId proxyAccount) {
			this.proxyAccount = proxyAccount;
		}

		public long getAutoRenew() {
			return autoRenew;
		}

		public void setAutoRenew(final long autoRenew) {
			this.autoRenew = autoRenew;
		}

		public long getExpiry() {
			return expiry;
		}

		public void setExpiry(final long expiry) {
			this.expiry = expiry;
		}
	}

	public static class Updater
			extends AbstractWorldUpdater<HederaWorldState, WorldStateAccount>
			implements HederaWorldUpdater {

		final Map<Address, Address> sponsorMap = new LinkedHashMap<>();

		protected Updater(final HederaWorldState world) { super(world); }

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
		public Address allocateNewContractAddress(final Address sponsor) {
			return wrappedWorldView().newContractAddress(sponsor);
		}

		public Map<Address, Address> getSponsorMap() {
			return sponsorMap;
		}

		@Override
		public void revert() {
			getDeletedAccounts().clear();
			getUpdatedAccounts().clear();
			for (int i = 0; i < sponsorMap.size(); i++) {
				wrappedWorldView().reclaimContractId();
			}
			sponsorMap.clear();
		}

		@Override
		public void commit() {
			final HederaWorldState wrapped = wrappedWorldView();
			wrapped.provisionalAccountDeletes.addAll(getDeletedAccountAddresses());
			wrapped.sponsorMap.putAll(sponsorMap);

			for (final UpdateTrackingAccount<WorldStateAccount> updated : getUpdatedAccounts()) {
				final WorldStateAccount origin = updated.getWrappedAccount();

				// Save the code in storage ...
				if (updated.codeWasUpdated()) {
					wrapped.provisionalCodeUpdates.put(updated.getAddress(), updated.getCode());
				}
				// ...and storage in the account trie first.
				final boolean freshState = origin == null || updated.getStorageWasCleared();
				if (freshState) {
					wrapped.repositoryRoot.delete(updated.getAddress().toArray());
				}

				final Map<UInt256, UInt256> updatedStorage = updated.getUpdatedStorage();
				if (!updatedStorage.isEmpty()) {
					// Apply any storage updates
					final ContractDetails storageTrie =
							freshState ? wrapped.repositoryRoot.getContractDetails(
									updated.getAddress().toArray()) : origin.storageTrie();
					final TreeSet<Map.Entry<UInt256, UInt256>> entries =
							new TreeSet<>(Map.Entry.comparingByKey());
					entries.addAll(updatedStorage.entrySet());

					for (final Map.Entry<UInt256, UInt256> entry : entries) {
						final UInt256 value = entry.getValue();
						if (value.isZero()) {
							storageTrie.put(DWUtil.fromUInt256(entry.getKey()), DataWord.ZERO);
						} else {
							storageTrie.put(DWUtil.fromUInt256(entry.getKey()), DWUtil.fromUInt256(value));
						}
					}
				}

				// TODO once UpdateTrackingAccount is extended - expiry, proxyAccount and autoRenew will be
				//  populated
				wrapped.provisionalAccountUpdates.put(updated.getAddress(),
						wrapped.new WorldStateAccount(updated.getAddress(), updated.getBalance()));
			}
		}

		@Override
		public WorldUpdater updater() {
			return new HederaStackedWorldStateUpdater(this, wrappedWorldView());
		}

		@Override
		public WorldStateAccount getHederaAccount(final Address address) {
			final HederaWorldState wrapped = wrappedWorldView();
			var result = wrapped.provisionalAccountUpdates.get(address);
			return result != null ? result : wrapped.get(address);
		}
	}
}
