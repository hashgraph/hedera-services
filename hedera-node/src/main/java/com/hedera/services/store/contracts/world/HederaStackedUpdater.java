//package com.hedera.services.store.contracts.world;/*
// * -
// * ‌
// * Hedera Services Node
// * ​
// * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
// * ​
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *       http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// * ‍
// *
// */
//
//import com.hedera.services.store.contracts.HederaUpdateTrackingAccount;
//import com.hedera.services.store.models.Id;
//import org.hyperledger.besu.datatypes.Address;
//import org.hyperledger.besu.datatypes.Wei;
//import org.hyperledger.besu.evm.account.EvmAccount;
//import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;
//
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class HederaStackedUpdater extends HederaAbstractWorldUpdater implements HederaWorldUpdater {
//
//	private int numberOfAddressesClaimed = 0;
//
//	private final Map<Address, HederaWorldState.WorldStateAccount> createdAccounts = new HashMap<>();
//	private final Map<Address, HederaUpdateTrackingAccount> updatedAccounts = new HashMap<>();
//	private final List<Address> deletedAccounts = new ArrayList<>();
//
//	private final HederaAbstractWorldUpdater parentUpdater;
//
//	HederaStackedUpdater(final HederaAbstractWorldUpdater world) {
//		super(world.hederaWorldState);
//		this.parentUpdater = world;
//	}
//
//	//returns account if this account exists in Hedera State
//	@Override
//	public HederaWorldState.WorldStateAccount get(Address address) {
//		if (deletedAccounts.contains(address)) {
//			return null;
//		} else if (updatedAccounts.containsKey(address)) {
//			return updatedAccounts.get(address).getWrappedAccount();
//		} else if (createdAccounts.containsKey(address)) {
//			return createdAccounts.get(address);
//		}
//		return parentUpdater.get(address);
//	}
//
//	//returns a modifiable object
//	@Override
//	public EvmAccount getAccount(final Address address) {
//		// We may have updated it already, so check that first.
//		final HederaUpdateTrackingAccount existing = updatedAccounts.get(address);
//		if (existing != null) {
//			return new WrappedEvmAccount(existing);
//		}
//		if (deletedAccounts.contains(address)) {
//			return null;
//		}
//
//		// Otherwise, get it from our wrapped view and create a new update tracker.
//		final HederaWorldState.WorldStateAccount origin = get(address);
//		if (origin == null) {
//			return null;
//		} else {
//			return new WrappedEvmAccount(track(new HederaUpdateTrackingAccount(origin)));
//		}
//	}
//
//	@Override
//	protected HederaUpdateTrackingAccount getForMutation(final Address address) {
//		final HederaUpdateTrackingAccount wrappedTracker = parentUpdater.updatedAccounts.get(address);
//		if (wrappedTracker != null) {
//			return wrappedTracker;
//		}
//		if (parentUpdater.deletedAccounts.contains(address)) {
//			return null;
//		}
//		// The wrapped one isn't tracking that account. We're creating a tracking "for him" (but
//		// don't add him yet to his tracking map) because we need it to satisfy the type system.
//		// We will recognize this case in commit below and use that tracker "pay back" our
//		// allocation, so this isn't lost.
//		return parentUpdater.getForMutation(address);
//	}
//
//	private HederaUpdateTrackingAccount track(final HederaUpdateTrackingAccount account) {
//		final Address address = account.getAddress();
//		updatedAccounts.put(address, account);
//		deletedAccounts.remove(address);
//		return account;
//	}
//
//	@Override
//	public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
//		HederaWorldState.WorldStateAccount created = parentUpdater.hederaWorldState.new WorldStateAccount(address, balance);
//		createdAccounts.put(address, created);
//		return new HederaUpdateTrackingAccount(created);
//	}
//
//	@Override
//	public void deleteAccount(final Address address) {
//		deletedAccounts.add(address);
//		updatedAccounts.remove(address);
//		createdAccounts.remove(address);
//	}
//
//	@Override
//	public Address allocateNewContractAddress(Address sponsor) {
//		numberOfAddressesClaimed++;
//		Id newContractId = parentUpdater.hederaWorldState.newContractId(sponsor);
//		return newContractId.asEvmAddress();
//	}
//
//	@Override
//	public void revert() {
//		deletedAccounts.clear();
//		updatedAccounts.clear();
//		for (int i = 0; i < numberOfAddressesClaimed; i++) {
//			parentUpdater.hederaWorldState.reclaimContractId();
//		}
//	}
//
//	@Override
//	public Collection<HederaUpdateTrackingAccount> getTouchedAccounts() {
//		return new ArrayList<>(updatedAccounts.values());
//	}
//
//	@Override
//	public Collection<Address> getDeletedAccountAddresses() {
//		return new ArrayList<>(deletedAccounts);
//	}
//
//	@Override
//	public void commit() {
//
//		// Our own updates should apply on top of the updates we're stacked on top, so our deletions
//		// may kill some of "their" updates, and our updates may review some of the account "they"
//		// deleted.
//		deletedAccounts.forEach(parentUpdater.updatedAccounts::remove);
//		updatedAccounts.values().forEach(a -> parentUpdater.deletedAccounts.remove(a.getAddress()));
//
//		// Then push our deletes and updates to the stacked ones.
//		parentUpdater.deletedAccounts.addAll(deletedAccounts);
//
//		for (final HederaUpdateTrackingAccount update : updatedAccounts.values()) {
//			HederaUpdateTrackingAccount trackingAccount = parentUpdater.updatedAccounts.get(update.getAddress());
//			if (trackingAccount == null) {
//				trackingAccount = new HederaUpdateTrackingAccount(update.getWrappedAccount());
//				parentUpdater.updatedAccounts.put(update.getAddress(), trackingAccount);
//			}
//			trackingAccount.setBalance(update.getBalance());
//
//			if (update.codeWasUpdated()) {
//				trackingAccount.setCode(update.getCode());
//			}
//			if (update.getStorageWasCleared()) {
//				trackingAccount.clearStorage();
//			}
//			update.getUpdatedStorage().forEach(trackingAccount::setStorageValue);
//		}
//
//		parentUpdater.createdAccounts.putAll(createdAccounts);
//	}
//}