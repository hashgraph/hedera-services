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
		 * for a stacked updater to re-create an account that was deleted by the updater below it.
		 * But that is not possible in Hedera. */
		getDeletedAccounts().forEach(wrapped.updatedAccounts::remove);
		wrapped.deletedAccounts.addAll(getDeletedAccounts());

		for (final var updatedAccount : getUpdatedAccounts()) {
			var mutable = wrapped.updatedAccounts.get(updatedAccount.getAddress());
			if (mutable == null) {
				// If we don't track this account, it's either a new one or getForMutation above had
				// created a tracker to satisfy the type system above and we can reuse that now.
				mutable = updatedAccount.getWrappedAccount();
				if (mutable == null) {
					// Brand new account, create our own version
					mutable = new UpdateTrackingLedgerAccount<>(updatedAccount.getAddress(), trackingAccounts());
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

		trackingLedgers().commit();
	}
}
