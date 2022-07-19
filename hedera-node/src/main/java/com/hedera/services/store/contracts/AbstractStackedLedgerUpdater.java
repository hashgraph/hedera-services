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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldView;

/**
 * Base implementation of a {@link org.hyperledger.besu.evm.worldstate.WorldUpdater} that buffers a
 * set of EVM account mutations (updates and deletions) that are mirrored in the accounts ledger of
 * a "parallel" {@link WorldLedgers} instance.
 *
 * <p>We need both representations of the change-set because the Besu EVM is implemented in terms of
 * the {@link Account} type hierarchy, while the logic for the native HTS pre-compiles is
 * implemented in terms of {@link com.hedera.services.ledger.TransactionalLedger} instances.
 *
 * @param <W> the type of the wrapped world view
 * @param <A> the account specialization used by the wrapped world view
 */
public abstract class AbstractStackedLedgerUpdater<W extends WorldView, A extends Account>
        extends AbstractLedgerWorldUpdater<
                AbstractLedgerWorldUpdater<W, A>, UpdateTrackingLedgerAccount<A>> {

    protected AbstractStackedLedgerUpdater(
            final AbstractLedgerWorldUpdater<W, A> world, final WorldLedgers trackingLedgers) {
        super(world, trackingLedgers);
    }

    /** {@inheritDoc} */
    @Override
    protected UpdateTrackingLedgerAccount<A> getForMutation(final Address address) {
        final var wrapped = wrappedWorldView();
        final var wrappedMutable = wrapped.updatedAccounts.get(address);
        if (wrappedMutable != null) {
            return wrappedMutable;
        }
        if (wrapped.deletedAccounts.contains(address)) {
            return null;
        }
        final A account = wrapped.getForMutation(address);
        return account == null
                ? null
                : new UpdateTrackingLedgerAccount<>(account, trackingAccounts());
    }

    /** {@inheritDoc} */
    @Override
    public void commit() {
        final var wrapped = wrappedWorldView();

        /* NOTE: In a traditional Ethereum context, it is possible with use of CREATE2 for a stacked updater
         * to re-create the very same account that was deleted by its parent updater. But since every Hedera
         * account gets a unique 0.0.X id---and corresponding unique mirror 0x0...0X address---that is not
         * possible here. So we needn't remove our updated accounts from our parent's deleted accounts. */
        getDeletedAccounts().forEach(wrapped.updatedAccounts::remove);
        wrapped.deletedAccounts.addAll(getDeletedAccounts());

        final var ledgers = trackingLedgers();
        /* We need to commit the ledgers first to make sure that any accounts we created exist in the parent,
         * so that any set(..., BALANCE, ...) calls made by the UpdateTrackingLedgerAccounts below will be
         * harmless repetitions of the balances we just flushed via commit().
         *
         * We filter any pending aliases to ensure the target address has actually been updated in this
         * frame (it is possible for a spawned child message to have reverted during creation, _without_
         * reverting this frame; and then the target address would not even exist.) */
        ledgers.aliases().filterPendingChanges(updatedAccounts::containsKey);
        ledgers.commit();
        for (final var updatedAccount : getUpdatedAccounts()) {
            /* First check if there is already a mutable tracker for this account in our parent updater;
             * if there is, we commit by propagating the changes from our tracker into that tracker. */
            var mutable = wrapped.updatedAccounts.get(updatedAccount.getAddress());
            if (mutable == null) {
                /* If the parent updater didn't have a mutable tracker, we must give it one. Unless
                 * we created this account (meaning our mutable tracker has no wrapped account), the
                 * "inner" mutable tracker we created in getForMutation() will do fine; we just need to
                 * update its tracking accounts to the parent's. */
                mutable = updatedAccount.getWrappedAccount();
                if (mutable == null) {
                    /* We created this account, so create a new tracker for our parent. */
                    mutable =
                            new UpdateTrackingLedgerAccount<>(
                                    updatedAccount.getAddress(), wrapped.trackingAccounts());
                } else {
                    /* This tracker is reusable, just update its tracking accounts to our parent's. */
                    mutable.updateTrackingAccounts(wrapped.trackingAccounts());
                }
                wrapped.updatedAccounts.put(mutable.getAddress(), mutable);
            }
            mutable.setNonce(updatedAccount.getNonce());

            if (!updatedAccount.wrappedAccountIsTokenProxy()) {
                mutable.setBalance(updatedAccount.getBalance());
            }
            if (updatedAccount.codeWasUpdated()) {
                mutable.setCode(updatedAccount.getCode());
            }
            if (updatedAccount.getStorageWasCleared()) {
                mutable.clearStorage();
            }
            updatedAccount.getUpdatedStorage().forEach(mutable::setStorageValue);
        }
        if (thisRecordSourceId != UNKNOWN_RECORD_SOURCE_ID) {
            wrapped.addCommittedRecordSourceId(thisRecordSourceId, recordsHistorian);
        }
        if (!committedRecordSourceIds.isEmpty()) {
            committedRecordSourceIds.forEach(
                    id -> wrapped.addCommittedRecordSourceId(id, recordsHistorian));
        }
    }
}
