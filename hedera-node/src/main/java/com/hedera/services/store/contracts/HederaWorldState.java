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

import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.worldstate.AbstractWorldUpdater;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import javax.inject.Inject;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.ledger.HederaLedger.CONTRACT_ID_COMPARATOR;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

public class HederaWorldState implements HederaMutableWorldState {
	private final EntityIdSource ids;
	private final EntityAccess entityAccess;
	private final Map<Address, Address> sponsorMap = new LinkedHashMap<>();
	private final List<ContractID> provisionalContractCreations = new LinkedList<>();

	@Inject
	public HederaWorldState(
			final EntityIdSource ids,
			final EntityAccess entityAccess
	) {
		this.ids = ids;
		this.entityAccess = entityAccess;
	}

	@Override
	public List<ContractID> persist() {

		final var copy = new ArrayList<>(provisionalContractCreations);
		provisionalContractCreations.clear();
		copy.sort(CONTRACT_ID_COMPARATOR);

		return copy;
	}

	@Override
	public void customizeSponsoredAccounts() {
		// copy over sponsor account info for CREATE operations
		sponsorMap.forEach((contract, sponsorAddress) -> {
			final var newlyCreated = accountParsedFromSolidityAddress(contract.toArray());
			final var sponsorAccount = accountParsedFromSolidityAddress(sponsorAddress.toArrayUnsafe());
			validateTrue(entityAccess.isExtant(newlyCreated), FAIL_INVALID);
			validateTrue(entityAccess.isExtant(sponsorAccount), FAIL_INVALID);

			final var sponsor = entityAccess.lookup(sponsorAccount);
			final var sponsorKey = sponsor.getAccountKey();
			final var createdKey = (sponsorKey instanceof JContractIDKey)
					? STATIC_PROPERTIES.scopedContractKeyWith(newlyCreated.getAccountNum())
					: sponsorKey;
			var customizer = new HederaAccountCustomizer()
					.key(createdKey)
					.memo(sponsor.getMemo())
					.proxy(sponsor.getProxy())
					.autoRenewPeriod(sponsor.getAutoRenewSecs())
					.expiry(sponsor.getExpiry())
					.isSmartContract(true);

			entityAccess.customize(newlyCreated, customizer);
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
		final var accountID = accountParsedFromSolidityAddress(address.toArray());
		if (!entityAccess.isExtant(accountID) || entityAccess.isDeleted(accountID)) {
			return null;
		}

		MerkleAccount account = entityAccess.lookup(accountID);
		final long expiry = account.getExpiry();
		final long balance = account.getBalance();
		final long autoRenewPeriod = account.getAutoRenewSecs();
		return new WorldStateAccount(address, Wei.of(balance), expiry, autoRenewPeriod, account.getProxy());
	}

	public class WorldStateAccount implements Account {

		private final Wei balance;
		private final AccountID account;
		private final Address address;
		private final byte[] bytesAddress;

		private JKey key;
		private String memo;
		private EntityId proxyAccount;
		private long expiry;
		private long autoRenew;

		public WorldStateAccount(
				final Address address,
				final Wei balance,
				final long expiry,
				final long autoRenew,
				final EntityId proxyAccount
		) {
			this.expiry = expiry;
			this.address = address;
			this.bytesAddress = address.toArray();
			this.balance = balance;
			this.autoRenew = autoRenew;
			this.proxyAccount = proxyAccount;
			this.account = accountParsedFromSolidityAddress(bytesAddress);
		}

		private EntityAccess storageTrie() {
			return entityAccess;
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
			return entityAccess.fetch(account);
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
			return storageTrie().get(account, key);
		}

		@Override
		public UInt256 getOriginalStorageValue(final UInt256 key) {
			return getStorageValue(key);
		}

		@Override
		public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
				final Bytes32 startKeyHash,
				final int limit
		) {
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
			extends AbstractWorldUpdater<HederaMutableWorldState, WorldStateAccount>
			implements HederaWorldUpdater {

		final Map<Address, Address> sponsorMap = new LinkedHashMap<>();
		Gas sbhRefund = Gas.ZERO;

		protected Updater(final HederaWorldState world) {
			super(world);
		}

		public Map<Address, Address> getSponsorMap() {
			return sponsorMap;
		}

		@Override
		protected WorldStateAccount getForMutation(final Address address) {
			final HederaWorldState wrapped = (HederaWorldState) wrappedWorldView();
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

		@Override
		public Gas getSbhRefund() {
			return sbhRefund;
		}

		@Override
		public void addSbhRefund(Gas refund) {
			sbhRefund = sbhRefund.plus(refund);
		}

		@Override
		public void revert() {
			getDeletedAccounts().clear();
			getUpdatedAccounts().clear();
			for (int i = 0; i < sponsorMap.size(); i++) {
				wrappedWorldView().reclaimContractId();
			}
			sponsorMap.clear();
			sbhRefund = Gas.ZERO;
		}

		@Override
		public void commit() {
			final HederaWorldState wrapped = (HederaWorldState) wrappedWorldView();
			wrapped.sponsorMap.putAll(sponsorMap);
			var entityAccess = wrapped.entityAccess;

			getDeletedAccountAddresses().forEach(address -> {
				final var accountID = accountParsedFromSolidityAddress(address.toArray());
				// account may have been created and deleted within the same transaction.
				if (!entityAccess.isExtant(accountID)) {
					entityAccess.spawn(accountID, 0, new HederaAccountCustomizer());
				}
				validateFalse(entityAccess.isDeleted(accountID), FAIL_INVALID);
				entityAccess.adjustBalance(accountID, -entityAccess.getBalance(accountID));
				entityAccess.customize(accountID, new HederaAccountCustomizer().isDeleted(true));
			});

			for (final UpdateTrackingAccount<WorldStateAccount> updated : getUpdatedAccounts()) {
				final var bytesAddress = updated.getAddress().toArray();
				final AccountID account = accountParsedFromSolidityAddress(bytesAddress);

				final var contractId = EntityIdUtils.asContract(account);
				if (!entityAccess.isExtant(account)) {
					entityAccess.spawn(account, 0, new HederaAccountCustomizer());
					wrapped.provisionalContractCreations.add(contractId);
				}

				final var oldBalance = entityAccess.getBalance(account);
				final var adjustment = updated.getBalance().toBigInteger().subtract(BigInteger.valueOf(oldBalance));
				entityAccess.adjustBalance(account, adjustment.longValue());

				final WorldStateAccount origin = updated.getWrappedAccount();
				// ...and storage in the account trie first.
				final boolean freshState = origin == null || updated.getStorageWasCleared();
				final Map<UInt256, UInt256> updatedStorage = updated.getUpdatedStorage();
				if (!updatedStorage.isEmpty()) {
					// Apply any storage updates
					final EntityAccess storageTrie =
							freshState ? wrapped.entityAccess : origin.storageTrie();
					final TreeSet<Map.Entry<UInt256, UInt256>> entries =
							new TreeSet<>(Map.Entry.comparingByKey());
					entries.addAll(updatedStorage.entrySet());

					for (final Map.Entry<UInt256, UInt256> entry : entries) {
						storageTrie.put(account, entry.getKey(), entry.getValue());
					}
				}

				// Save the code in storage ...
				if (updated.codeWasUpdated()) {
					entityAccess.store(account, updated.getCode());
				}
			}
		}

		@Override
		public WorldUpdater updater() {
			return new HederaStackedWorldStateUpdater(this, wrappedWorldView());
		}

		@Override
		public WorldStateAccount getHederaAccount(final Address address) {
			return getForMutation(address);
		}
	}
}
