package com.hedera.services.store.contracts;

import com.hedera.services.store.models.Id;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.AbstractWorldUpdater;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/*
 * - HederaAbstractWorldUpdater must use {@link HederaUpdateTrackingAccount} instead of {@link UpdateTrackingAccount}
 * - HederaAbstractWorldUpdater must have new method: allocateNewContractAddress.
 * The method will:
 *       - count the number of times the method is called
 *       - call the `HederaWorldView` to allocate new ID from {@link com.hedera.services.ledger.ids.SeqNoEntityIdSource}
 * - HederaAbstractWorldUpdater must have new method: reclaimContractAddress
 * The method will:
 * 	     - call the `HederaWorldView` to reclaim an ID from {@link com.hedera.services.ledger.ids.SeqNoEntityIdSource}
 * 	     - decrement the counter for `newContractAddressesAllocated`
 * - StackedUpdater in HederaAbstractWorldUpdater must extend `HederaAbstractWorldUpdater` instead of {@link AbstractWorldUpdater}
 * - HederaAbstractWorldUpdater on { UpdateTrackingAccount.reset} must clear the number of times `allocateNewContractAddress` was called
 * - HederaAbstractWorldUpdater on { UpdateTrackingAccount.revert} must call the
 * `HederaWorldView` and execute { com.hedera.services.ledger.ids.SeqNoEntityIdSource.reclaim} `newContractAddressesAllocated` times
 */
public abstract class HederaAbstractWorldUpdater extends AbstractWorldUpdater<HederaWorldState, HederaWorldState.WorldStateAccount> implements HederaWorldUpdater {

	public final HederaWorldState world;
	private int numberOfAddressesClaimed = 0;
	protected Map<Address, HederaUpdateTrackingAccount> updatedAccounts = new HashMap<>();

	protected HederaAbstractWorldUpdater(HederaWorldState world) {
		super(world);
		this.world = world;
	}

	@Override
	public void revert() {
		for (int i = 0; i < numberOfAddressesClaimed; i++) {
			wrappedWorldView().reclaimContractId();
		}
	}

	@Override
	public Address allocateNewContractAddress(Address sponsor) {
		numberOfAddressesClaimed++;
		Id newContractId = wrappedWorldView().newContractId(sponsor);
		return newContractId.asEvmAddress();
	}

	public void reclaimContractAddresses() {
		numberOfAddressesClaimed--;
		wrappedWorldView().reclaimContractId();
	}

	protected HederaUpdateTrackingAccount track(final HederaUpdateTrackingAccount account) {
		final Address address = account.getAddress();
		updatedAccounts.put(address, account);
		deletedAccounts.remove(address);
		return account;
	}

	@Override
	public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
		final HederaUpdateTrackingAccount account = new HederaUpdateTrackingAccount(world. new WorldStateAccount(address, balance));
		return new WrappedEvmAccount(track(account));
	}

	@Override
	public void deleteAccount(final Address address) {
		deletedAccounts.add(address);
		updatedAccounts.remove(address);
	}

	@Override
	protected void reset() {
		super.reset();
		numberOfAddressesClaimed = 0;
	}

	@Override
	public HederaWorldUpdater updater() {
		return new HederaStackedUpdater(this);
	}

	@Override
	protected HederaWorldState wrappedWorldView() {
		return world;
	}

	public static class HederaStackedUpdater
			extends AbstractWorldUpdater<HederaAbstractWorldUpdater, HederaUpdateTrackingAccount> implements HederaWorldUpdater {

		HederaStackedUpdater(final HederaAbstractWorldUpdater world) {
			super(world);
		}

		@Override
		protected HederaUpdateTrackingAccount getForMutation(final Address address) {
			final HederaAbstractWorldUpdater wrapped = wrappedWorldView();
			final HederaUpdateTrackingAccount wrappedTracker = wrapped.updatedAccounts.get(address);
			if (wrappedTracker != null) {
				return wrappedTracker;
			}
			if (wrapped.deletedAccounts.contains(address)) {
				return null;
			}
			// The wrapped one isn't tracking that account. We're creating a tracking "for him" (but
			// don't add him yet to his tracking map) because we need it to satisfy the type system.
			// We will recognize this case in commit below and use that tracker "pay back" our
			// allocation, so this isn't lost.
			final HederaWorldState.WorldStateAccount account = wrappedWorldView().getForMutation(address);
			return account == null ? null : new HederaUpdateTrackingAccount(account);
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
		public void revert() {
			getDeletedAccounts().clear();
			getUpdatedAccounts().clear();
		}

		@Override
		public void commit() {
			final HederaAbstractWorldUpdater wrapped = wrappedWorldView();
			// Our own updates should apply on top of the updates we're stacked on top, so our deletions
			// may kill some of "their" updates, and our updates may review some of the account "they"
			// deleted.
			getDeletedAccounts().forEach(wrapped.updatedAccounts::remove);
			getUpdatedAccounts().forEach(a -> wrapped.deletedAccounts.remove(a.getAddress()));

			// Then push our deletes and updates to the stacked ones.
			wrapped.deletedAccounts.addAll(getDeletedAccounts());

			for (final UpdateTrackingAccount<HederaUpdateTrackingAccount> update : getUpdatedAccounts()) {
				HederaUpdateTrackingAccount existing = wrapped.updatedAccounts.get(update.getAddress());
				if (existing == null) {
					// If we don't track this account, it's either a new one or getForMutation above had
					// created a tracker to satisfy the type system above and we can reuse that now.
					existing = update.getWrappedAccount();
					if (existing == null) {
						// Brand new account, create our own version todo is this correct?
						existing = new HederaUpdateTrackingAccount(wrapped.world. new WorldStateAccount(update.getAddress(), update.getBalance()));
					}
					wrapped.updatedAccounts.put(existing.getAddress(), existing);
				}
				existing.setNonce(update.getNonce());
				existing.setBalance(update.getBalance());
				if (update.codeWasUpdated()) {
					existing.setCode(update.getCode());
				}
				if (update.getStorageWasCleared()) {
					existing.clearStorage();
				}
				update.getUpdatedStorage().forEach(existing::setStorageValue);
			}
		}

		@Override
		public Address allocateNewContractAddress(Address sponsor) {
			// TODO implement me
			return null;
		}
	}
}