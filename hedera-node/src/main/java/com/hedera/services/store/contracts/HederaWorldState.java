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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.ContractCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.BytesComparator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
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

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.ledger.HederaLedger.CONTRACT_ID_COMPARATOR;
import static com.hedera.services.store.contracts.HederaWorldState.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;
import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.asContract;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

@Singleton
public class HederaWorldState implements HederaMutableWorldState {
	private static final Code EMPTY_CODE = new Code(Bytes.EMPTY, Hash.hash(Bytes.EMPTY));
	private static final String TOKEN_BYTECODE_PATTERN = "fefefefefefefefefefefefefefefefefefefefe";
	private static final String TOKEN_CALL_REDIRECT_CONTRACT_BINARY =
			"6080604052348015600f57600080fd5b506000610167905077618dc65efefefefefefefefefefefefefefefefefefefefe600052366000602037600080366018016008845af43d806000803e8160008114605857816000f35b816000fdfea2646970667358221220d8378feed472ba49a0005514ef7087017f707b45fb9bf56bb81bb93ff19a238b64736f6c634300080b0033";

	private final EntityIdSource ids;
	private final EntityAccess entityAccess;
	private final SigImpactHistorian sigImpactHistorian;
	private final List<ContractID> provisionalContractCreations = new LinkedList<>();
	private final CodeCache codeCache;
	private final GlobalDynamicProperties dynamicProperties;

	// If non-null, the new contract customizations requested by the HAPI contractCreate sender
	private ContractCustomizer hapiSenderCustomizer;

	@Inject
	public HederaWorldState(
			final EntityIdSource ids,
			final EntityAccess entityAccess,
			final CodeCache codeCache,
			final SigImpactHistorian sigImpactHistorian,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.ids = ids;
		this.entityAccess = entityAccess;
		this.codeCache = codeCache;
		this.sigImpactHistorian = sigImpactHistorian;
		this.dynamicProperties = dynamicProperties;
	}

	/* Used to manage static calls. */
	public HederaWorldState(
			final EntityIdSource ids,
			final EntityAccess entityAccess,
			final CodeCache codeCache,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.ids = ids;
		this.entityAccess = entityAccess;
		this.codeCache = codeCache;
		this.sigImpactHistorian = null;
		this.dynamicProperties = dynamicProperties;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ContractCustomizer hapiSenderCustomizer() {
		return hapiSenderCustomizer;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setHapiSenderCustomizer(final ContractCustomizer customizer) {
		hapiSenderCustomizer = customizer;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void resetHapiSenderCustomizer() {
		hapiSenderCustomizer = null;
	}

	@Override
	public List<ContractID> getCreatedContractIds() {
		final var copy = new ArrayList<>(provisionalContractCreations);
		provisionalContractCreations.clear();
		copy.sort(CONTRACT_ID_COMPARATOR);
		return copy;
	}

	@Override
	public Address newContractAddress(Address sponsor) {
		final var newContractId = ids.newContractId(accountIdFromEvmAddress(sponsor));
		return asTypedEvmAddress(newContractId);
	}

	@Override
	public void reclaimContractId() {
		ids.reclaimLastId();
	}

	@Override
	public Updater updater() {
		return new Updater(this, entityAccess.worldLedgers().wrapped(), dynamicProperties);
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
	public WorldStateAccount get(final @Nullable Address address) {
		if (address == null) {
			return null;
		}

		if (entityAccess.isTokenAccount(address) && dynamicProperties.isRedirectTokenCallsEnabled()) {
			return new WorldStateTokenAccount(address, EntityId.fromAddress(address));
		}

		final var accountId = accountIdFromEvmAddress(address);

		if (!isGettable(accountId)) {
			return null;
		}

		final long expiry = entityAccess.getExpiry(accountId);
		final long balance = entityAccess.getBalance(accountId);
		final long autoRenewPeriod = entityAccess.getAutoRenew(accountId);

		return new WorldStateAccount(address, Wei.of(balance), expiry, autoRenewPeriod,
				entityAccess.getProxy(accountId));
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
			this.account = accountIdFromEvmAddress(address);
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

		private Code getCodeInternal() {
			final var code = codeCache.getIfPresent(address);
			return (code == null) ? EMPTY_CODE : code;
		}
	}

	public class WorldStateTokenAccount extends WorldStateAccount {
		public static final long TOKEN_PROXY_ACCOUNT_NONCE = -1;

		public WorldStateTokenAccount(final Address address,
									  final EntityId proxyAccount) {
			super(address, Wei.of(0), 0, 0, proxyAccount);
		}

		@Override
		public Bytes getCode() {
			return Bytes.fromHexString(TOKEN_CALL_REDIRECT_CONTRACT_BINARY.replace(TOKEN_BYTECODE_PATTERN,
					getAddress().toUnprefixedHexString()));
		}

		@Override
		public long getNonce() {
			return TOKEN_PROXY_ACCOUNT_NONCE;
		}
	}

	public static class Updater
			extends AbstractLedgerWorldUpdater<HederaMutableWorldState, WorldStateAccount>
			implements HederaWorldUpdater {

		Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges = new TreeMap<>(BytesComparator.INSTANCE);
		GlobalDynamicProperties dynamicProperties;

		private int numAllocatedIds = 0;
		private Gas sbhRefund = Gas.ZERO;

		protected Updater(final HederaWorldState world, final WorldLedgers trackingLedgers, GlobalDynamicProperties dynamicProperties) {
			super(world, trackingLedgers);
			this.dynamicProperties = dynamicProperties;
		}

		public Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> getStateChanges() {
			return stateChanges;
		}

		public Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> getFinalStateChanges() {
			this.addAllStorageUpdatesToStateChanges();
			return stateChanges;
		}

		@SuppressWarnings("unchecked")
		private void addAllStorageUpdatesToStateChanges() {
			for (UpdateTrackingLedgerAccount<? extends Account> uta :
					(Collection<UpdateTrackingLedgerAccount<? extends Account>>) this.getTouchedAccounts()) {
				final var storageUpdates = uta.getUpdatedStorage().entrySet();
				if (!storageUpdates.isEmpty()) {
					final Map<Bytes, Pair<Bytes, Bytes>> accountChanges =
							stateChanges.computeIfAbsent(uta.getAddress(), a -> new TreeMap<>(BytesComparator.INSTANCE));
					for (Map.Entry<UInt256, UInt256> entry : storageUpdates) {
						UInt256 key = entry.getKey();
						UInt256 originalStorageValue = uta.getOriginalStorageValue(key);
						UInt256 updatedStorageValue = uta.getStorageValue(key);
						accountChanges.put(key, new ImmutablePair<>(originalStorageValue, updatedStorageValue));
					}
				}
			}
		}

		@Override
		public ContractCustomizer customizerForPendingCreation() {
			// If the base updater is asked for a customizer, it's because the originating message call
			// was a CONTRACT_CREATION; so we must have details from a HAPI ContractCreate
			final var hapiCustomizer = wrappedWorldView().hapiSenderCustomizer();
			if (hapiCustomizer == null) {
				throw new IllegalStateException("Base updater asked for customizer, but no details from HAPI are set");
			}
			return hapiCustomizer;
		}

		@Override
		protected WorldStateAccount getForMutation(final Address address) {
			final HederaWorldState wrapped = (HederaWorldState) wrappedWorldView();
			return wrapped.get(address);
		}

		@Override
		public Address newContractAddress(final Address sponsor) {
			numAllocatedIds++;
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
			while (numAllocatedIds != 0) {
				wrapped.reclaimContractId();
				numAllocatedIds--;
			}
			sbhRefund = Gas.ZERO;
		}

		@Override
		public void countIdsAllocatedByStacked(final int n) {
			numAllocatedIds += n;
		}

		@Override
		public void commit() {
			final HederaWorldState wrapped = (HederaWorldState) wrappedWorldView();
			final var entityAccess = wrapped.entityAccess;
			final var impactHistorian = wrapped.sigImpactHistorian;

			commitSizeLimitedStorageTo(entityAccess);

			final var deletedAddresses = getDeletedAccountAddresses();
			deletedAddresses.forEach(address -> {
				final var accountId = accountIdFromEvmAddress(address);
				validateTrue(impactHistorian != null, FAIL_INVALID);
				impactHistorian.markEntityChanged(accountId.getAccountNum());
				ensureExistence(accountId, entityAccess, wrapped.provisionalContractCreations);
			});
			for (final var updatedAccount : getUpdatedAccounts()) {
				if (updatedAccount.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE) {
					continue;
				}
				final var accountId = accountIdFromEvmAddress(updatedAccount.getAddress());
				ensureExistence(accountId, entityAccess, wrapped.provisionalContractCreations);
				if (updatedAccount.codeWasUpdated()) {
					entityAccess.storeCode(accountId, updatedAccount.getCode());
				}
			}

			entityAccess.recordNewKvUsageTo(trackingAccounts());
			// Because we have tracked all account creations, deletions, and balance changes in the ledgers,
			// this commit() persists all of that information without any additional use of the deletedAccounts
			// or updatedAccounts collections.
			trackingLedgers().commit(impactHistorian);
		}

		private void ensureExistence(
				final AccountID accountId,
				final EntityAccess entityAccess,
				final List<ContractID> provisionalContractCreations
		) {
			if (!entityAccess.isExtant(accountId)) {
				provisionalContractCreations.add(asContract(accountId));
			}
		}

		private void commitSizeLimitedStorageTo(final EntityAccess entityAccess) {
			for (final var updatedAccount : getUpdatedAccounts()) {
				final var accountId = accountIdFromEvmAddress(updatedAccount.getAddress());
				// Note that we don't have the equivalent of an account-scoped storage trie, so we can't
				// do anything in particular when updated.getStorageWasCleared() is true. (We will address
				// this in our global state expiration implementation.)
				final var kvUpdates = updatedAccount.getUpdatedStorage();
				if (!kvUpdates.isEmpty()) {
					kvUpdates.forEach((key, value) -> entityAccess.putStorage(accountId, key, value));
				}
			}
			entityAccess.flushStorage();
		}

		@Override
		public WorldUpdater updater() {
			return new HederaStackedWorldStateUpdater(this, wrappedWorldView(),
					trackingLedgers().wrapped(), dynamicProperties
			);
		}

		@Override
		public WorldStateAccount getHederaAccount(final Address address) {
			return getForMutation(address);
		}
	}
}