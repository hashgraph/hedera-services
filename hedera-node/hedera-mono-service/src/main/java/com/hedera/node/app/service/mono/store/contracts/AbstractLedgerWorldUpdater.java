/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.ALIAS;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.asLiteralString;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.models.UpdateTrackingAccount;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.accounts.ContractCustomizer;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.UpdateAccountTrackerImpl;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WorldView;

/**
 * Provides implementation help for both "base" and "stacked" {@link WorldUpdater}s.
 *
 * <p>The key internal invariant of the class is that it makes consistent use of its (1) {@code
 * deletedAccounts} set and {@code updatedAccounts} map; and (2) the {@code accounts} tracking
 * ledger from its {@code trackingLedgers}.
 *
 * <p>Without running an HTS precompile, the "internal" information flow is one-way, from (1) to
 * (2). There are three cases:
 *
 * <ol>
 *   <li>When an address is added to the {@code deletedAccounts}, it is also marked deleted in the
 *       {@code accounts} ledger.
 *   <li>When an address is added to the {@code updatedAccounts} map via {@link
 *       AbstractLedgerWorldUpdater#createAccount(Address, long, Wei)}, it is also spawned in the
 *       {@code accounts} ledger.
 *   <li>When {@link UpdateTrackingAccount#setBalance(Wei)} is called on a (mutable) tracking
 *       account, the same balance change is made in the {@code accounts} ledger.
 * </ol>
 *
 * When an HTS precompile is run, the commit to the {@code acccounts} ledger is then intercepted so
 * that balance changes are reflected in the {@code updatedAccounts} map.
 *
 * <p>Concrete subclasses must then manage the "external" information flow from these data
 * structures to their wrapped {@link WorldView} in a {@link HederaWorldUpdater#commit()}
 * implementation. This will certainly involve calling {@link WorldLedgers#commit()}, and then
 * merging the {@code deletedAccounts} and {@code updatedAccounts} with the parent {@link
 * org.hyperledger.besu.evm.worldstate.WorldState} in some way.
 *
 * @param <A> the most specialized account type to be updated
 * @param <W> the most specialized world updater to be used
 */
public abstract class AbstractLedgerWorldUpdater<W extends WorldView, A extends Account>
        extends AbstractLedgerEvmWorldUpdater<W, A> {
    protected static final int UNKNOWN_RECORD_SOURCE_ID = -1;

    private final WorldLedgers trackingLedgers;

    // All the record source ids of our committed child updaters
    protected int thisRecordSourceId = UNKNOWN_RECORD_SOURCE_ID;
    protected List<Integer> committedRecordSourceIds = Collections.emptyList();
    protected RecordsHistorian recordsHistorian = null;

    protected AbstractLedgerWorldUpdater(final W world, final WorldLedgers trackingLedgers) {
        super(world, new AccountAccessorImpl(trackingLedgers));
        this.trackingLedgers = trackingLedgers;
    }

    /**
     * Returns the {@link ContractCustomizer} to use for the pending creation.
     *
     * @return the pending creation's customizer
     */
    public abstract ContractCustomizer customizerForPendingCreation();

    @Override
    public MutableAccount createAccount(final Address addressOrAlias, final long nonce, final Wei balance) {
        final var curAliases = aliases();
        final var address = curAliases.resolveForEvm(addressOrAlias);

        final var curAccounts = trackingAccounts();
        final var newMutable = new UpdateTrackingAccount<A>(address, new UpdateAccountTrackerImpl(curAccounts));
        if (trackingLedgers.areMutable()) {
            final var newAccountId = accountIdFromEvmAddress(newMutable.getAddress());
            curAccounts.create(newAccountId);
            if (curAliases.isInUse(addressOrAlias)) {
                curAccounts.set(newAccountId, ALIAS, ByteString.copyFrom(addressOrAlias.toArrayUnsafe()));
            }
            customizerForPendingCreation().customize(newAccountId, curAccounts);
        }

        newMutable.setNonce(nonce);
        newMutable.setBalance(balance);

        return track(newMutable);
    }

    @Override
    public Account get(final Address addressOrAlias) {
        if (!addressOrAlias.equals(accountAccessor.canonicalAddress(addressOrAlias))) {
            return null;
        }

        final var address = aliases().resolveForEvm(addressOrAlias);

        final var extantMutable = this.updatedAccounts.get(address);
        if (extantMutable != null) {
            return extantMutable;
        } else {
            if (this.deletedAccounts.contains(address)) {
                return null;
            }
            if (this.world.getClass() == HederaWorldState.class) {
                return this.world.get(address);
            }
            return this.world.get(addressOrAlias);
        }
    }

    @Override
    public MutableAccount getAccount(final Address address) {
        final var extantMutable = updatedAccounts.get(address);
        if (extantMutable != null) {
            return extantMutable;
        } else if (deletedAccounts.contains(address)) {
            return null;
        } else {
            final var origin = getForMutation(address);
            if (origin == null) {
                return null;
            }
            final var newMutable =
                    new UpdateTrackingAccount<>(origin, new UpdateAccountTrackerImpl(trackingLedgers.accounts()));
            return track(newMutable);
        }
    }

    @Override
    public void deleteAccount(final Address addressOrAlias) {
        final var address = aliases().resolveForEvm(addressOrAlias);
        deletedAccounts.add(address);
        updatedAccounts.remove(address);
        if (trackingLedgers.areMutable()) {
            final var accountId = EntityIdUtils.accountIdFromEvmAddress(address);
            final var curAccounts = trackingLedgers.accounts();
            curAccounts.set(accountId, IS_DELETED, true);
            if (!revoke(addressOrAlias, accountId)) {
                final var alias = (ByteString) curAccounts.get(accountId, ALIAS);
                if (!alias.isEmpty()) {
                    revoke(Address.wrap(Bytes.wrap(alias.toByteArray())), accountId);
                }
            }
        }
    }

    @Override
    public Optional<WorldUpdater> parentUpdater() {
        if (world instanceof WorldUpdater updater) {
            return Optional.of(updater);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void revert() {
        getDeletedAccounts().clear();
        getUpdatedAccountsCollection().clear();
        trackingLedgers().revert();

        if (recordsHistorian != null) {
            recordsHistorian.revertChildRecordsFromSource(thisRecordSourceId);
            committedRecordSourceIds.forEach(recordsHistorian::revertChildRecordsFromSource);
        }
    }

    public void manageInProgressRecord(
            final RecordsHistorian recordsHistorian,
            final ExpirableTxnRecord.Builder recordSoFar,
            final TransactionBody.Builder syntheticBody) {
        this.manageInProgressRecord(recordsHistorian, recordSoFar, syntheticBody, Collections.emptyList());
    }

    public void manageInProgressRecord(
            final RecordsHistorian recordsHistorian,
            final ExpirableTxnRecord.Builder recordSoFar,
            final TransactionBody.Builder syntheticBody,
            final List<TransactionSidecarRecord.Builder> sidecarRecords) {
        ensureFamiliarityWith(recordsHistorian);
        if (thisRecordSourceId == UNKNOWN_RECORD_SOURCE_ID) {
            thisRecordSourceId = recordsHistorian.nextChildRecordSourceId();
        }
        recordsHistorian.trackFollowingChildRecord(thisRecordSourceId, syntheticBody, recordSoFar, sidecarRecords);
    }

    public void manageInProgressPrecedingRecord(
            final RecordsHistorian recordsHistorian,
            final ExpirableTxnRecord.Builder recordSoFar,
            final TransactionBody.Builder syntheticBody) {
        ensureFamiliarityWith(recordsHistorian);
        if (thisRecordSourceId == UNKNOWN_RECORD_SOURCE_ID) {
            thisRecordSourceId = recordsHistorian.nextChildRecordSourceId();
        }
        recordsHistorian.trackPrecedingChildRecord(thisRecordSourceId, syntheticBody, recordSoFar);
    }

    public WorldLedgers wrappedTrackingLedgers(final SideEffectsTracker sideEffectsTracker) {
        return withChangeObserver(trackingLedgers.wrapped(sideEffectsTracker));
    }

    protected void addCommittedRecordSourceId(final int recordSourceId, final RecordsHistorian recordsHistorian) {
        ensureFamiliarityWith(recordsHistorian);
        if (committedRecordSourceIds.isEmpty()) {
            committedRecordSourceIds = new ArrayList<>();
        }
        committedRecordSourceIds.add(recordSourceId);
    }

    private void ensureFamiliarityWith(final RecordsHistorian recordsHistorian) {
        if (this.recordsHistorian == null) {
            this.recordsHistorian = recordsHistorian;
        } else {
            if (this.recordsHistorian != recordsHistorian) {
                throw new IllegalArgumentException("Encountered multiple historians in the same transaction");
            }
        }
    }

    private WorldLedgers withChangeObserver(final WorldLedgers wrappedLedgers) {
        final var wrappedAccounts = wrappedLedgers.accounts();
        if (wrappedAccounts != null) {
            wrappedAccounts.setPropertyChangeObserver(this::onAccountPropertyChange);
        }
        return wrappedLedgers;
    }

    private void onAccountPropertyChange(final AccountID id, final AccountProperty property, final Object newValue) {
        final var address = EntityIdUtils.asTypedEvmAddress(id);
        /* Impossible with a well-behaved precompile, as our wrapped accounts should also show this as deleted */
        if (deletedAccounts.contains(address)) {
            throw new IllegalArgumentException("A wrapped tracking ledger tried to change the "
                    + "balance of deleted account "
                    + asLiteralString(id)
                    + " to "
                    + newValue);
        }
        var updatedAccount = updatedAccounts.get(address);
        if (updatedAccount == null) {
            final var origin = getForMutation(address);
            if (origin == null) {
                // we are observing property change for a freshly
                // created lazy account through the HTS precompiled contract,
                // which can occur with any transfer precompile function
                updatedAccount =
                        new UpdateTrackingAccount<>(address, new UpdateAccountTrackerImpl(trackingLedgers.accounts()));
            } else {
                updatedAccount =
                        new UpdateTrackingAccount<>(origin, new UpdateAccountTrackerImpl(trackingLedgers.accounts()));
            }
            track(updatedAccount);
        }
        if (property == BALANCE) {
            final var newBalance = (long) newValue;
            updatedAccount.setBalanceFromPropertyChangeObserver(Wei.of(newBalance));
        }
    }

    public WorldLedgers trackingLedgers() {
        return trackingLedgers;
    }

    public void trackLazilyCreatedAccount(final Address address) {
        final var newMutable = new UpdateTrackingAccount<A>(address, new UpdateAccountTrackerImpl(trackingAccounts()));
        track(newMutable);
    }

    protected Collection<Address> getDeletedAccounts() {
        return deletedAccounts;
    }

    protected Collection<UpdateTrackingAccount<A>> getUpdatedAccountsCollection() {
        return updatedAccounts.values();
    }

    public TransactionalLedger<AccountID, AccountProperty, HederaAccount> trackingAccounts() {
        return trackingLedgers.accounts();
    }

    protected TransactionalLedger<TokenID, TokenProperty, MerkleToken> trackingTokens() {
        return trackingLedgers.tokens();
    }

    public ContractAliases aliases() {
        return trackingLedgers.aliases();
    }

    /* --- Internal helpers --- */
    private boolean revoke(final Address address, final AccountID accountId) {
        final var curAliases = aliases();
        if (curAliases.isInUse(address)) {
            curAliases.unlink(address);
            trackingAccounts().set(accountId, ALIAS, ByteString.EMPTY);
            return true;
        } else {
            return false;
        }
    }

    @VisibleForTesting
    List<Integer> getCommittedRecordSourceIds() {
        return committedRecordSourceIds;
    }

    @VisibleForTesting
    RecordsHistorian getRecordsHistorian() {
        return recordsHistorian;
    }
}
