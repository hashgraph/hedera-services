package com.hedera.services.store.contracts;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldView;

public abstract class AbstractStackedLedgerUpdater<W extends WorldView, A extends Account>
		extends AbstractLedgerWorldUpdater<AbstractLedgerWorldUpdater<W, A>, UpdateTrackingLedgerAccount<A>> {

	protected AbstractStackedLedgerUpdater(
			final AbstractLedgerWorldUpdater<W, A> world,
			final WorldLedgers trackingLedgers
	) {
		super(world, trackingLedgers);
	}

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
		return account == null ? null : new UpdateTrackingLedgerAccount<>(account, trackingAccounts());
	}

	@Override
	public void commit() {
		final var wrapped = wrappedWorldView();

		/* NOTE - in a traditional Ethereum context, it is technically possible with use of CREATE2
		 * for a stacked updater to re-create an account that was deleted by its parent updater. But
		 * that is not possible in Hedera. */
		getDeletedAccounts().forEach(wrapped.updatedAccounts::remove);
		wrapped.deletedAccounts.addAll(getDeletedAccounts());

		/* We need to commit the ledgers first to make sure that any accounts we created exist in the parent,
		* so that any set(..., BALANCE, ...) calls made by the UpdateTrackingLedgerAccounts below will be
		* harmless repetitions of the balances we just flushed via commit(). */
		trackingLedgers().commit();
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
					mutable = new UpdateTrackingLedgerAccount<>(updatedAccount.getAddress(), wrapped.trackingAccounts());
				} else {
					/* This tracker is reusable, just update its tracking accounts to our parent's. */
					mutable.updateTrackingAccounts(wrapped.trackingAccounts());
				}
				wrapped.updatedAccounts.put(mutable.getAddress(), mutable);
			}
			mutable.setNonce(updatedAccount.getNonce());
			mutable.setBalance(updatedAccount.getBalance());
			if (updatedAccount.codeWasUpdated()) {
				mutable.setCode(updatedAccount.getCode());
			}
			if (updatedAccount.getStorageWasCleared()) {
				mutable.clearStorage();
			}
			updatedAccount.getUpdatedStorage().forEach(mutable::setStorageValue);
		}
	}
}
