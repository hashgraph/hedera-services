/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.store.contracts;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.ledger.HederaLedger.CONTRACT_ID_COMPARATOR;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.asContract;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.node.app.service.mono.utils.ResourceValidationUtils.validateResourceLimit;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.WorldStateAccount;
import com.hedera.node.app.service.evm.store.models.UpdateTrackingAccount;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.ContractCustomizer;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.hedera.node.app.service.mono.throttling.annotations.HandleThrottle;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    private static final Logger log = LogManager.getLogger(HederaWorldState.class);

    private final UsageLimits usageLimits;
    private final EntityIdSource ids;
    private final EntityAccess entityAccess;
    private final FunctionalityThrottling handleThrottling;
    private final SigImpactHistorian sigImpactHistorian;
    private final List<ContractID> provisionalContractCreations = new LinkedList<>();
    private final Map<ContractID, Long> contractNonces =
            new TreeMap<>(Comparator.comparingLong(ContractID::getContractNum));
    private final GlobalDynamicProperties dynamicProperties;
    private final RecordsHistorian recordsHistorian;
    private final CodeCache codeCache;
    // If non-null, the new contract customizations requested by the HAPI contractCreate sender
    private ContractCustomizer hapiSenderCustomizer;

    @Inject
    public HederaWorldState(
            final UsageLimits usageLimits,
            final EntityIdSource ids,
            final EntityAccess entityAccess,
            final CodeCache codeCache,
            final SigImpactHistorian sigImpactHistorian,
            final GlobalDynamicProperties dynamicProperties,
            final @HandleThrottle FunctionalityThrottling handleThrottling,
            @NonNull final RecordsHistorian recordsHistorian) {
        this.ids = ids;
        this.usageLimits = usageLimits;
        this.entityAccess = entityAccess;
        this.codeCache = codeCache;
        this.sigImpactHistorian = sigImpactHistorian;
        this.dynamicProperties = dynamicProperties;
        this.handleThrottling = handleThrottling;
        this.recordsHistorian = recordsHistorian;
    }

    /* Used to manage static calls. */
    public HederaWorldState(
            final EntityIdSource ids,
            final EntityAccess entityAccess,
            final CodeCache codeCache,
            final GlobalDynamicProperties dynamicProperties) {
        this.ids = ids;
        this.entityAccess = entityAccess;
        this.usageLimits = null;
        this.handleThrottling = null;
        this.sigImpactHistorian = null;
        this.recordsHistorian = null;
        this.codeCache = codeCache;
        this.dynamicProperties = dynamicProperties;
    }

    public Account get(final Address address) {
        if (address == null) {
            return null;
        }
        if (entityAccess.isTokenAccount(address) && dynamicProperties.isRedirectTokenCallsEnabled()) {
            return new HederaEvmWorldStateTokenAccount(address);
        }
        if (!entityAccess.isUsable(address)) {
            return null;
        }
        final long balance = entityAccess.getBalance(address);
        return new WorldStateAccount(address, Wei.of(balance), codeCache, entityAccess);
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
    public void clearProvisionalContractCreations() {
        provisionalContractCreations.clear();
    }

    @Override
    public void clearContractNonces() {
        contractNonces.clear();
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
        // We MUST return a copy of the list, and not the list itself,
        // because we might immediately clear the list after returning
        // it for use in the transaction record (in case the next
        // transaction we handle is also a contract operation)
        final var copy = new ArrayList<>(provisionalContractCreations);
        copy.sort(CONTRACT_ID_COMPARATOR);
        return copy;
    }

    @Override
    public Map<ContractID, Long> getContractNonces() {
        return contractNonces;
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
    public Stream<StreamableAccount> streamAccounts(Bytes32 startKeyHash, int limit) {
        throw new UnsupportedOperationException();
    }

    public static class Updater extends AbstractLedgerWorldUpdater<HederaMutableWorldState, Account>
            implements HederaEvmWorldUpdater, HederaWorldUpdater {

        Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges = new TreeMap<>();
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
            for (UpdateTrackingAccount<? extends Account> uta :
                    (Collection<UpdateTrackingAccount<? extends Account>>) this.getTouchedAccounts()) {
                final var storageUpdates = uta.getUpdatedStorage().entrySet();
                if (!storageUpdates.isEmpty()) {
                    final Map<Bytes, Pair<Bytes, Bytes>> accountChanges =
                            stateChanges.computeIfAbsent(uta.getAddress(), a -> new TreeMap<>());
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
            // If the base updater is asked for a customizer, it's because the originating message
            // call
            // was a CONTRACT_CREATION; so we must have details from a HAPI ContractCreate
            final var hapiCustomizer = wrappedWorldView().hapiSenderCustomizer();
            if (hapiCustomizer == null) {
                throw new IllegalStateException("Base updater asked for customizer, but no details from HAPI are set");
            }
            return hapiCustomizer;
        }

        @Override
        public boolean hasPendingCreationCustomizer() {
            return wrappedWorldView().hapiSenderCustomizer() != null;
        }

        @Override
        public Account getForMutation(final Address address) {
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
            final var updatedAccounts = getUpdatedAccountsCollection();

            trackNewlyCreatedAccounts(
                    entityAccess,
                    wrapped.provisionalContractCreations,
                    wrapped.contractNonces,
                    impactHistorian,
                    getDeletedAccountAddresses(),
                    updatedAccounts);
            if (!wrapped.provisionalContractCreations.isEmpty()) {
                final var n = wrapped.provisionalContractCreations.size();
                Objects.requireNonNull(wrapped.usageLimits).assertCreatableContracts(n);
                if (dynamicProperties.shouldEnforceAccountCreationThrottleForContracts()) {
                    final var creationCapacity = !Objects.requireNonNull(wrapped.handleThrottling)
                            .shouldThrottleNOfUnscaled(n, HederaFunctionality.CryptoCreate);
                    validateResourceLimit(creationCapacity, CONSENSUS_GAS_EXHAUSTED);
                }
            }
            final var consThrottleCapacityIsAvailable =
                    Objects.requireNonNull(wrapped.recordsHistorian).hasThrottleCapacityForChildTransactions();
            validateResourceLimit(consThrottleCapacityIsAvailable, CONSENSUS_GAS_EXHAUSTED);
            // Throws an ITE if any storage limit is exceeded, or if storage fees cannot be paid
            commitSizeLimitedStorageTo(entityAccess, updatedAccounts, wrapped.codeCache);
            entityAccess.recordNewKvUsageTo(trackingAccounts());

            // Because we have tracked all account creations, deletions, and balance changes in the
            // ledgers, this commit() persists all of that information without any additional use
            // of the deletedAccounts or updatedAccounts collections
            trackingLedgers().commit(impactHistorian);
        }

        private void trackNewlyCreatedAccounts(
                final EntityAccess entityAccess,
                final List<ContractID> provisionalCreations,
                final Map<ContractID, Long> contractNonces,
                final SigImpactHistorian impactHistorian,
                final Collection<Address> deletedAddresses,
                final Collection<UpdateTrackingAccount<Account>> updatedAccounts) {
            deletedAddresses.forEach(address -> {
                final var accountId = accountIdFromEvmAddress(address);
                validateTrue(impactHistorian != null, FAIL_INVALID);
                impactHistorian.markEntityChanged(accountId.getAccountNum());
                trackIfNewlyCreated(accountId, entityAccess, provisionalCreations);
            });
            for (final var updatedAccount : updatedAccounts) {
                if (updatedAccount.getNonce() == HederaEvmWorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE) {
                    continue;
                }

                final var accountId = accountIdFromEvmAddress(updatedAccount.getAddress());
                trackIfNewlyCreated(accountId, entityAccess, provisionalCreations);

                if (dynamicProperties.isContractsNoncesExternalizationEnabled()) {
                    trackContractNonces(accountId, entityAccess, contractNonces);
                }
            }
        }

        void trackIfNewlyCreated(
                final AccountID accountId,
                final EntityAccess entityAccess,
                final List<ContractID> provisionalContractCreations) {
            final var accounts = trackingAccounts();
            if (!accounts.contains(accountId)) {
                log.error("Account {} missing in tracking ledgers", accountId);
                return;
            }
            final var isSmartContract = (Boolean) trackingAccounts().get(accountId, AccountProperty.IS_SMART_CONTRACT);
            if (Boolean.TRUE.equals(isSmartContract) && !entityAccess.isExtant(asTypedEvmAddress(accountId))) {
                provisionalContractCreations.add(asContract(accountId));
            }
        }

        void trackContractNonces(
                final AccountID accountId,
                final EntityAccess entityAccess,
                final Map<ContractID, Long> contractNonces) {
            final var accounts = trackingAccounts();
            if (!accounts.contains(accountId)) {
                log.error("Account {} missing in tracking ledgers", accountId);
                return;
            }
            final var isSmartContract = (Boolean) trackingAccounts().get(accountId, AccountProperty.IS_SMART_CONTRACT);
            if (Boolean.TRUE.equals(isSmartContract)) {
                final var trackingNonce = ((long) trackingAccounts().get(accountId, AccountProperty.ETHEREUM_NONCE));
                if (entityAccess.isExtant(asTypedEvmAddress(accountId))) {
                    final var stateNonce = entityAccess.getNonce(asTypedEvmAddress(accountId));
                    if (trackingNonce > stateNonce) {
                        contractNonces.put(asContract(accountId), trackingNonce);
                    }
                } else {
                    contractNonces.put(asContract(accountId), trackingNonce);
                }
            }
        }

        private void commitSizeLimitedStorageTo(
                final EntityAccess entityAccess,
                final Collection<UpdateTrackingAccount<Account>> updatedAccounts,
                final CodeCache codeCache) {
            for (final var updatedAccount : updatedAccounts) {
                // We don't check updatedAccount.getStorageWasCleared(), because we only purge
                // storage
                // slots when a contract has expired and is being permanently removed from state
                final var accountId = accountIdFromEvmAddress(updatedAccount.getAddress());
                final var kvUpdates = updatedAccount.getUpdatedStorage();
                if (!kvUpdates.isEmpty()) {
                    kvUpdates.forEach((key, value) -> entityAccess.putStorage(accountId, key, value));
                }
            }
            entityAccess.flushStorage(trackingAccounts());
            for (final var updatedAccount : updatedAccounts) {
                if (updatedAccount.codeWasUpdated()) {
                    final var code = updatedAccount.getCode();
                    final var address = updatedAccount.getAddress();
                    final var accountId = accountIdFromEvmAddress(address);
                    entityAccess.storeCode(accountId, code);
                    // If an account's code was updated, 99.9% of the time this will be because
                    // it was just created (and hence could not have any cached bytecode)...but
                    // it's also possible to finalize a hollow account as a contract, and in this
                    // case the bytecode changes from empty to non-empty; so just be completely
                    // safe and invalidate any out-of-date code in the code cache here
                    codeCache.invalidateIfPresentAndNot(address, code);
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
