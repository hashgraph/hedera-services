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
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
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
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.ledger.HederaLedger.CONTRACT_ID_COMPARATOR;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asTypedSolidityAddress;
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
	public List<ContractID> persistProvisionalContractCreations() {
		final var copy = new ArrayList<>(provisionalContractCreations);
		provisionalContractCreations.clear();
		copy.sort(CONTRACT_ID_COMPARATOR);

		return copy;
	}

	@Override
	public void customizeSponsoredAccounts() {
		sponsorMap.forEach((contract, sponsorAddress) -> {
			final var createdId = EntityNum.fromAddress(contract);
			final var sponsorId = EntityNum.fromAddress(sponsorAddress);
			validateTrue(entityAccess.isExtant(createdId), FAIL_INVALID);
			validateTrue(entityAccess.isExtant(sponsorId), FAIL_INVALID);

			final var sponsorKey = entityAccess.getKey(sponsorId);
			final var createdKey = (sponsorKey instanceof JContractIDKey)
					? STATIC_PROPERTIES.scopedContractKeyWith(createdId.longValue())
					: sponsorKey;
			final var customizer = new HederaAccountCustomizer()
					.key(createdKey)
					.memo(entityAccess.getMemo(sponsorId))
					.proxy(entityAccess.getProxy(sponsorId))
					.autoRenewPeriod(entityAccess.getAutoRenew(sponsorId))
					.expiry(entityAccess.getExpiry(sponsorId))
					.isSmartContract(true);

			entityAccess.customize(createdId, customizer);
		});
		sponsorMap.clear();
	}

	@Override
	public Address newContractAddress(Address sponsor) {
		final var newContractId = ids.newContractId(accountParsedFromSolidityAddress(sponsor));
		return asTypedSolidityAddress(newContractId);
	}

	@Override
	public void reclaimContractId() {
		ids.reclaimLastId();
	}

	@Override
	public Updater updater() {
		return new Updater(this, entityAccess.worldLedgers().wrapped());
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
	public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public WorldStateAccount get(final Address address) {
		final var accountId = EntityNum.fromAddress(address);
		if (!isGettable(accountId)) {
			return null;
		}

		final long expiry = entityAccess.getExpiry(accountId);
		final long balance = entityAccess.getBalance(accountId);
		final long autoRenewPeriod = entityAccess.getAutoRenew(accountId);
		return new WorldStateAccount(address, Wei.of(balance), expiry, autoRenewPeriod,
				entityAccess.getProxy(accountId));
	}

	private boolean isGettable(final EntityNum id) {
		return entityAccess.isExtant(id) && !entityAccess.isDeleted(id) && !entityAccess.isDetached(id);
	}

	public class WorldStateAccount implements Account {
		private final Wei balance;
		private final EntityNum account;
		private final Address address;

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
			this.balance = balance;
			this.autoRenew = autoRenew;
			this.proxyAccount = proxyAccount;
			this.account = EntityNum.fromAddress(address);
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
			return entityAccess.fetchCode(account);
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
			return entityAccess.getStorage(account, key);
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
			throw new UnsupportedOperationException();
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

		public EntityNum getAccount() {
			return account;
		}
	}

	public static class Updater
			extends AbstractLedgerWorldUpdater<HederaMutableWorldState, WorldStateAccount>
			implements HederaWorldUpdater {

		private static final HederaAccountCustomizer CONTRACT_CUSTOMIZER =
				new HederaAccountCustomizer().isSmartContract(true);

		private final Map<Address, Address> sponsorMap = new LinkedHashMap<>();

		private Gas sbhRefund = Gas.ZERO;

		protected Updater(final HederaWorldState world, final WorldLedgers trackingLedgers) {
			super(world, trackingLedgers);
		}

		@Override
		public Map<Address, Address> getSponsorMap() {
			return sponsorMap;
		}

		@Override
		protected WorldStateAccount getForMutation(final Address address) {
			final HederaWorldState wrapped = (HederaWorldState) wrappedWorldView();
			return wrapped.get(address);
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
			super.revert();

			final var wrapped = wrappedWorldView();
			for (int i = 0, n = sponsorMap.size(); i < n; i++) {
				wrapped.reclaimContractId();
			}
			sponsorMap.clear();
			sbhRefund = Gas.ZERO;
		}

		@Override
		public void commit() {
			final HederaWorldState wrapped = (HederaWorldState) wrappedWorldView();
			final var entityAccess = wrapped.entityAccess;

			/* Note that both the adjustBalance() and spawn() calls in the blocks below are ONLY
			 * needed to make sure the record's ℏ transfer list is constructed properly---the
			 * finishing call to trackingLedgers().commits() at the end of this method will persist
			 * all the same information. */
			final var deletedAddresses = getDeletedAccountAddresses();
			deletedAddresses.forEach(address -> {
				final var accountId = EntityNum.fromAddress(address);
				final var deletedBalance= entityAccess.getBalance(accountId);
				entityAccess.adjustBalance(accountId, -deletedBalance);
			});
			for (final var updatedAccount : getUpdatedAccounts()) {
				final var accountNum = EntityNum.fromAddress(updatedAccount.getAddress());

				if (!entityAccess.isExtant(accountNum)) {
					wrapped.provisionalContractCreations.add(accountNum.toGrpcContractId());
					entityAccess.spawn(accountNum, 0L, CONTRACT_CUSTOMIZER);
				}
				final var balanceChange = updatedAccount.getBalance().toLong() - entityAccess.getBalance(accountNum);
				entityAccess.adjustBalance(accountNum, balanceChange);

				/* Note that we don't have the equivalent of an account-scoped storage  trie, so we can't
				 * do anything in particular when updated.getStorageWasCleared() is true. (We will address
				 * this in our global state expiration implementation.) */
				final Map<UInt256, UInt256> updatedStorage = updatedAccount.getUpdatedStorage();
				if (!updatedStorage.isEmpty()) {
					final TreeSet<Map.Entry<UInt256, UInt256>> entries = new TreeSet<>(Map.Entry.comparingByKey());
					entries.addAll(updatedStorage.entrySet());
					for (final var entry : entries) {
						entityAccess.putStorage(accountNum, entry.getKey(), entry.getValue());
					}
				}
				if (updatedAccount.codeWasUpdated()) {
					entityAccess.storeCode(accountNum, updatedAccount.getCode());
				}
			}

			/* Because we have tracked all account creations, deletions, and balance changes in the ledgers,
			this commit() persists all of that information without any additional use of the deletedAccounts
			or updatedAccounts collections. */
			trackingLedgers().commit();

			wrapped.sponsorMap.putAll(sponsorMap);
		}

		@Override
		public WorldUpdater updater() {
			return new HederaStackedWorldStateUpdater(this, wrappedWorldView(), trackingLedgers().wrapped());
		}

		@Override
		public WorldStateAccount getHederaAccount(final Address address) {
			return getForMutation(address);
		}
	}
}
