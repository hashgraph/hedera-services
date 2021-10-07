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
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.ethereum.core.AccountState;
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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.contractParsedFromSolidityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

@Singleton
public class HederaWorldState implements HederaMutableWorldState {

	private final EntityIdSource ids;
	private final HederaLedger ledger;
	private final ServicesRepositoryRoot repositoryRoot;
	private final Map<Address, Address> sponsorMap = new LinkedHashMap<>();
	private final List<ContractID> provisionalContractCreations = new LinkedList<>();

	@Inject
	public HederaWorldState(
			final EntityIdSource ids,
			final HederaLedger ledger,
			final ServicesRepositoryRoot repositoryRoot
	) {
		this.ids = ids;
		this.repositoryRoot = repositoryRoot;
		this.ledger = ledger;
	}

	@Override
	public List<ContractID> persist() {
		repositoryRoot.flush();

		final var copy = new LinkedList<>(provisionalContractCreations);
		provisionalContractCreations.clear();

		return copy;
	}

	@Override
	public void customizeSponsoredAccounts() {
		// copy over sponsor account info for CREATE operations
		sponsorMap.forEach((contract, sponsorAddress) -> {
			AccountID newlyCreated = accountParsedFromSolidityAddress(contract.toArray());
			AccountID sponsorAccount = accountParsedFromSolidityAddress(sponsorAddress.toArrayUnsafe());
			validateTrue(ledger.exists(newlyCreated), FAIL_INVALID);
			validateTrue(ledger.exists(sponsorAccount), FAIL_INVALID);

			final var sponsor = ledger.get(sponsorAccount);
			var customizer = new HederaAccountCustomizer()
					.key(sponsor.getAccountKey())
					.memo(sponsor.getMemo())
					.proxy(sponsor.getProxy())
					.autoRenewPeriod(sponsor.getAutoRenewSecs())
					.expiry(sponsor.getExpiry())
					.isSmartContract(true);

			ledger.customize(newlyCreated, customizer);
		});
		sponsorMap.clear();
	}

	@Override
	public Address newContractAddress(Address sponsor) {
		final var newContractId =
				ids.newContractId(accountParsedFromSolidityAddress(sponsor.toArrayUnsafe()));
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
		if (!repositoryRoot.isExist(address.toArray()) || repositoryRoot.isDeleted(address.toArray())) {
			return null;
		}

		AccountState accountState = repositoryRoot.getAccountState(address.toArray());
		final long expiry = accountState.getExpirationTime();
		final EntityId proxy = new EntityId(accountState.getProxyAccountShard(),
				accountState.getProxyAccountRealm(), accountState.getProxyAccountNum());
		final long balance = accountState.getBalance().longValue();
		final long autoRenewPeriod = accountState.getAutoRenewPeriod();
		return new WorldStateAccount(address, Wei.of(balance), expiry, autoRenewPeriod, proxy);
	}

	public class WorldStateAccount implements Account {

		private final Wei balance;
		private final Address address;

		private JKey key;
		private String memo;
		private EntityId proxyAccount;
		private long expiry;
		private long autoRenew;

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
			var codeBytes = repositoryRoot.getCode(getAddress().toArray());
			return codeBytes == null ? Bytes.EMPTY : Bytes.of(codeBytes);
		}

		public EntityId getProxyAccount() {
			return proxyAccount;
		}

		public void setProxyAccount(EntityId proxyAccount) {
			this.proxyAccount = proxyAccount;
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
			wrapped.sponsorMap.putAll(sponsorMap);
			var repository = wrapped.repositoryRoot;

			getDeletedAccountAddresses().forEach(address -> {
				final var bytesAddress = address.toArray();
				// account may have been created and deleted within the same transaction.
				validateFalse(repository.isDeleted(bytesAddress), FAIL_INVALID);
				repository.addBalance(bytesAddress, repository.getBalance(bytesAddress).negate());
				repository.setDeleted(bytesAddress, true);
			});

			for (final UpdateTrackingAccount<WorldStateAccount> updated : getUpdatedAccounts()) {
				final byte[] address = updated.getAddress().toArray();

				if (!repository.isExist(address)) {
					repository.delete(address);
					repository.createAccount(address);
					wrapped.provisionalContractCreations.add(contractParsedFromSolidityAddress(address));
				}

				final var oldBalance = repository.getBalance(address);
				final var adjustment = updated.getBalance().toBigInteger().subtract(oldBalance);
				repository.addBalance(address, adjustment);

				final WorldStateAccount origin = updated.getWrappedAccount();
				// ...and storage in the account trie first.
				final boolean freshState = origin == null || updated.getStorageWasCleared();
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
				// Save the code in storage ...
				if (updated.codeWasUpdated()) {
					repository.saveCode(address, updated.getCode().toArray());
				}
			}
		}

		@Override
		public WorldUpdater updater() {
			return new HederaStackedWorldStateUpdater(this, wrappedWorldView());
		}

		@Override
		public WorldStateAccount getHederaAccount(final Address address) {
			final HederaWorldState wrapped = wrappedWorldView();
			return wrapped.get(address);
		}
	}
}
