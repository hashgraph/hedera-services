/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.store.contracts;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.ledger.HederaLedger.CONTRACT_ID_COMPARATOR;
import static com.hedera.services.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;
import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.asContract;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.ContractCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.utils.BytesComparator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

@Singleton
public class HederaWorldState implements HederaMutableWorldState {
    private final UsageLimits usageLimits;
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
            final UsageLimits usageLimits,
            final EntityIdSource ids,
            final EntityAccess entityAccess,
            final CodeCache codeCache,
            final SigImpactHistorian sigImpactHistorian,
            final GlobalDynamicProperties dynamicProperties) {
        this.ids = ids;
        this.usageLimits = usageLimits;
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
            final GlobalDynamicProperties dynamicProperties) {
        this.ids = ids;
        this.entityAccess = entityAccess;
        this.codeCache = codeCache;
        this.usageLimits = null;
        this.sigImpactHistorian = null;
        this.dynamicProperties = dynamicProperties;
    }

    /** {@inheritDoc} */
    @Override
    public ContractCustomizer hapiSenderCustomizer() {
        return hapiSenderCustomizer;
    }

    /** {@inheritDoc} */
    @Override
    public void setHapiSenderCustomizer(final ContractCustomizer customizer) {
        hapiSenderCustomizer = customizer;
    }

    /** {@inheritDoc} */
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
    public Account get(final @Nullable Address address) {
        if (address == null) {
            return null;
        }
        if (entityAccess.isTokenAccount(address)
                && dynamicProperties.isRedirectTokenCallsEnabled()) {
            return new WorldStateTokenAccount(address);
        }
        final var accountId = accountIdFromEvmAddress(address);
        if (!isGettable(accountId)) {
            return null;
        }
        final long balance = entityAccess.getBalance(accountId);
        return new WorldStateAccount(address, Wei.of(balance), codeCache, entityAccess);
    }

    private boolean isGettable(final AccountID id) {
        return entityAccess.isUsable(id);
    }

    public static class Updater extends AbstractLedgerWorldUpdater<HederaMutableWorldState, Account>
            implements HederaWorldUpdater {

        Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges =
                new TreeMap<>(BytesComparator.INSTANCE);
        GlobalDynamicProperties dynamicProperties;

        private int numAllocatedIds = 0;
        private long sbhRefund = 0L;

        protected Updater(
                final HederaWorldState world,
                final WorldLedgers trackingLedgers,
                final GlobalDynamicProperties dynamicProperties) {
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
                    (Collection<UpdateTrackingLedgerAccount<? extends Account>>)
                            this.getTouchedAccounts()) {
                final var storageUpdates = uta.getUpdatedStorage().entrySet();
                if (!storageUpdates.isEmpty()) {
                    final Map<Bytes, Pair<Bytes, Bytes>> accountChanges =
                            stateChanges.computeIfAbsent(
                                    uta.getAddress(), a -> new TreeMap<>(BytesComparator.INSTANCE));
                    for (Map.Entry<UInt256, UInt256> entry : storageUpdates) {
                        UInt256 key = entry.getKey();
                        UInt256 originalStorageValue = uta.getOriginalStorageValue(key);
                        UInt256 updatedStorageValue = uta.getStorageValue(key);
                        accountChanges.put(
                                key,
                                new ImmutablePair<>(originalStorageValue, updatedStorageValue));
                    }
                }
            }
        }

        @Override
        public ContractCustomizer customizerForPendingCreation() {
            // If the base updater is asked for a customizer, it's because the originating message
            // call
            // was a CONTRACT_CREATION; so we must have details from a HAPI ContractCreate
            final var hapiCustomizer = wrappedWorldView().hapiSenderCustomizer();
            if (hapiCustomizer == null) {
                throw new IllegalStateException(
                        "Base updater asked for customizer, but no details from HAPI are set");
            }
            return hapiCustomizer;
        }

        @Override
        protected Account getForMutation(final Address address) {
            final HederaWorldState wrapped = (HederaWorldState) wrappedWorldView();
            return wrapped.get(address);
        }

        @Override
        public Address newContractAddress(final Address sponsor) {
            numAllocatedIds++;
            return wrappedWorldView().newContractAddress(sponsor);
        }

        @Override
        public long getSbhRefund() {
            return sbhRefund;
        }

        @Override
        public void addSbhRefund(long refund) {
            sbhRefund = sbhRefund + refund;
        }

        @Override
        public void revert() {
            super.revert();
            final var wrapped = wrappedWorldView();
            while (numAllocatedIds != 0) {
                wrapped.reclaimContractId();
                numAllocatedIds--;
            }
            sbhRefund = 0L;
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
            final var updatedAccounts = getUpdatedAccounts();

            trackNewlyCreatedAccounts(
                    entityAccess,
                    wrapped.provisionalContractCreations,
                    impactHistorian,
                    getDeletedAccountAddresses(),
                    updatedAccounts);
            if (!wrapped.provisionalContractCreations.isEmpty()) {
                Objects.requireNonNull(wrapped.usageLimits)
                        .assertCreatableContracts(wrapped.provisionalContractCreations.size());
            }
            // Throws an ITE if any storage limit is exceeded, or if storage fees cannot be paid
            commitSizeLimitedStorageTo(entityAccess, updatedAccounts);
            entityAccess.recordNewKvUsageTo(trackingAccounts());

            // Because we have tracked all account creations, deletions, and balance changes in the
            // ledgers, this commit() persists all of that information without any additional use
            // of the deletedAccounts or updatedAccounts collections
            trackingLedgers().commit(impactHistorian);
        }

        private void trackNewlyCreatedAccounts(
                final EntityAccess entityAccess,
                final List<ContractID> provisionalCreations,
                final SigImpactHistorian impactHistorian,
                final Collection<Address> deletedAddresses,
                final Collection<UpdateTrackingLedgerAccount<Account>> updatedAccounts) {
            deletedAddresses.forEach(
                    address -> {
                        final var accountId = accountIdFromEvmAddress(address);
                        validateTrue(impactHistorian != null, FAIL_INVALID);
                        impactHistorian.markEntityChanged(accountId.getAccountNum());
                        trackIfNewlyCreated(accountId, entityAccess, provisionalCreations);
                    });
            for (final var updatedAccount : updatedAccounts) {
                if (updatedAccount.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE) {
                    continue;
                }
                final var accountId = accountIdFromEvmAddress(updatedAccount.getAddress());
                trackIfNewlyCreated(accountId, entityAccess, provisionalCreations);
            }
        }

        private void trackIfNewlyCreated(
                final AccountID accountId,
                final EntityAccess entityAccess,
                final List<ContractID> provisionalContractCreations) {
            if (!entityAccess.isExtant(accountId)) {
                provisionalContractCreations.add(asContract(accountId));
            }
        }

        private void commitSizeLimitedStorageTo(
                final EntityAccess entityAccess,
                final Collection<UpdateTrackingLedgerAccount<Account>> updatedAccounts) {
            for (final var updatedAccount : updatedAccounts) {
                // We don't check updatedAccount.getStorageWasCleared(), because we only purge
                // storage
                // slots when a contract has expired and is being permanently removed from state
                final var accountId = updatedAccount.getAccountId();
                final var kvUpdates = updatedAccount.getUpdatedStorage();
                if (!kvUpdates.isEmpty()) {
                    kvUpdates.forEach(
                            (key, value) -> entityAccess.putStorage(accountId, key, value));
                }
            }
            entityAccess.flushStorage(trackingAccounts());
            for (final var updatedAccount : updatedAccounts) {
                if (updatedAccount.codeWasUpdated()) {
                    entityAccess.storeCode(updatedAccount.getAccountId(), updatedAccount.getCode());
                }
            }
        }

        @Override
        public WorldUpdater updater() {
            return new HederaStackedWorldStateUpdater(
                    this, wrappedWorldView(), trackingLedgers().wrapped(), dynamicProperties);
        }
    }
}
