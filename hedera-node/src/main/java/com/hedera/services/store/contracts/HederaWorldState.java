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

import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.stream.Stream;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.ledger.HederaLedger.CONTRACT_ID_COMPARATOR;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asContract;
import static com.hedera.services.utils.EntityIdUtils.asTypedSolidityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

@Singleton
public class HederaWorldState implements HederaMutableWorldState {
	private static final Code EMPTY_CODE = new Code(Bytes.EMPTY, Hash.hash(Bytes.EMPTY));

	private final EntityIdSource ids;
	private final EntityAccess entityAccess;
	private final SigImpactHistorian sigImpactHistorian;
	private final Map<Address, Address> sponsorMap = new LinkedHashMap<>();
	private final List<ContractID> provisionalContractCreations = new LinkedList<>();
	private final CodeCache codeCache;

	@Inject
	public HederaWorldState(
			final EntityIdSource ids,
			final EntityAccess entityAccess,
			final CodeCache codeCache,
			final SigImpactHistorian sigImpactHistorian
	) {
		this.ids = ids;
		this.entityAccess = entityAccess;
		this.codeCache = codeCache;
		this.sigImpactHistorian = sigImpactHistorian;
	}

	public HederaWorldState(
			final EntityIdSource ids,
			final EntityAccess entityAccess,
			final CodeCache codeCache
	) {
		this.ids = ids;
		this.entityAccess = entityAccess;
		this.codeCache = codeCache;
		this.sigImpactHistorian = null;
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
			final var createdId = accountParsedFromSolidityAddress(contract);
			final var sponsorId = accountParsedFromSolidityAddress(sponsorAddress);
			validateTrue(entityAccess.isExtant(createdId), FAIL_INVALID);
			validateTrue(entityAccess.isExtant(sponsorId), FAIL_INVALID);

			final var sponsorKey = entityAccess.getKey(sponsorId);
			final var createdKey = (sponsorKey instanceof JContractIDKey)
					? STATIC_PROPERTIES.scopedContractKeyWith(createdId.getAccountNum())
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
	public WorldStateAccount get(Address address) {
		final var accountID = accountParsedFromSolidityAddress(address);
		if (!isGettable(accountID)) {
			return null;
		}

		final long expiry = entityAccess.getExpiry(accountID);
		final long balance = entityAccess.getBalance(accountID);
		final long autoRenewPeriod = entityAccess.getAutoRenew(accountID);
		return new WorldStateAccount(address, Wei.of(balance), expiry, autoRenewPeriod,
				entityAccess.getProxy(accountID));
	}

	private boolean isGettable(final AccountID id) {
		return entityAccess.isExtant(id) && !entityAccess.isDeleted(id) && !entityAccess.isDetached(id);
	}

	public class WorldStateAccount implements Account {
		private final Wei balance;
		private final AccountID account;
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
			this.account = accountParsedFromSolidityAddress(address);
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
			return getCodeInternal().getBytes();
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
			return getCodeInternal().getCodeHash();
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

		public AccountID getAccount() {
			return account;
		}

		private Code getCodeInternal() {
			final var code = codeCache.getIfPresent(address);
			return (code == null) ? EMPTY_CODE : code;
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
			final var impactHistorian = wrapped.sigImpactHistorian;

			commitSizeLimitedStorageTo(entityAccess);

			/* Note that both the adjustBalance() and spawn() calls in the blocks below are ONLY
			 * needed to make sure the record's ℏ transfer list is constructed properly---the
			 * finishing call to trackingLedgers().commits() at the end of this method will persist
			 * all the same information. */
			final var deletedAddresses = getDeletedAccountAddresses();
			deletedAddresses.forEach(address -> {
				final var accountId = accountParsedFromSolidityAddress(address);
				validateTrue(impactHistorian != null, FAIL_INVALID);
				impactHistorian.markEntityChanged(accountId.getAccountNum());
				ensureExistence(accountId, entityAccess, wrapped.provisionalContractCreations);
				final var deletedBalance = entityAccess.getBalance(accountId);
				entityAccess.adjustBalance(accountId, -deletedBalance);
			});
			for (final var updatedAccount : getUpdatedAccounts()) {
				final var accountId = accountParsedFromSolidityAddress(updatedAccount.getAddress());

				ensureExistence(accountId, entityAccess, wrapped.provisionalContractCreations);
				final var balanceChange = updatedAccount.getBalance().toLong() - entityAccess.getBalance(accountId);
				entityAccess.adjustBalance(accountId, balanceChange);

				if (updatedAccount.codeWasUpdated()) {
					entityAccess.storeCode(accountId, updatedAccount.getCode());
				}
			}

			entityAccess.recordNewKvUsageTo(trackingAccounts());
			/* Because we have tracked all account creations, deletions, and balance changes in the ledgers,
			this commit() persists all of that information without any additional use of the deletedAccounts
			or updatedAccounts collections. */
			trackingLedgers().commit();

			wrapped.sponsorMap.putAll(sponsorMap);
		}

		private void ensureExistence(
				final AccountID accountId,
				final EntityAccess entityAccess,
				final List<ContractID> provisionalContractCreations
		) {
			if (!entityAccess.isExtant(accountId)) {
				provisionalContractCreations.add(asContract(accountId));
				entityAccess.spawn(accountId, 0L, CONTRACT_CUSTOMIZER);
			}
		}

		private void commitSizeLimitedStorageTo(final EntityAccess entityAccess) {
			for (final var updatedAccount : getUpdatedAccounts()) {
				final var accountId = accountParsedFromSolidityAddress(updatedAccount.getAddress());
				/* Note that we don't have the equivalent of an account-scoped storage trie, so we can't
				 * do anything in particular when updated.getStorageWasCleared() is true. (We will address
				 * this in our global state expiration implementation.) */
				final var kvUpdates = updatedAccount.getUpdatedStorage();
				if (!kvUpdates.isEmpty()) {
					kvUpdates.forEach((key, value) -> entityAccess.putStorage(accountId, key, value));
				}
			}
			entityAccess.flushStorage();
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
