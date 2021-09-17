//package com.hedera.services.store.contracts.world;
//
///*
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
//import org.hyperledger.besu.evm.account.Account;
//import org.hyperledger.besu.evm.account.EvmAccount;
//import org.hyperledger.besu.evm.worldstate.WorldUpdater;
//import org.hyperledger.besu.evm.worldstate.WorldView;
//import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;
//
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//
///*
// * - HederaAbstractWorldUpdater must use {@link HederaUpdateTrackingAccount} instead of {@link UpdateTrackingAccount}
// * - HederaAbstractWorldUpdater must have new method: allocateNewContractAddress.
// * The method will:
// *       - count the number of times the method is called
// *       - call the `HederaWorldView` to allocate new ID from {@link com.hedera.services.ledger.ids.SeqNoEntityIdSource}
// * - HederaAbstractWorldUpdater must have new method: reclaimContractAddress
// * The method will:
// * 	     - call the `HederaWorldView` to reclaim an ID from {@link com.hedera.services.ledger.ids.SeqNoEntityIdSource}
// * 	     - decrement the counter for `newContractAddressesAllocated`
// * - StackedUpdater in HederaAbstractWorldUpdater must extend `HederaAbstractWorldUpdater` instead of {@link AbstractWorldUpdater}
// * - HederaAbstractWorldUpdater on { UpdateTrackingAccount.reset} must clear the number of times `allocateNewContractAddress` was called
// * - HederaAbstractWorldUpdater on { UpdateTrackingAccount.revert} must call the
// * `HederaWorldView` and execute { com.hedera.services.ledger.ids.SeqNoEntityIdSource.reclaim} `newContractAddressesAllocated` times
// */
//public abstract class HederaAbstractWorldUpdater<W extends WorldView>
//		implements HederaWorldUpdater {
//
//	private final W world;
//
//	private int numberOfAddressesClaimed = 0;
//
//	private final Map<Address, HederaWorldState.WorldStateAccount> createdAccounts = new HashMap<>();
//	private final Map<Address, HederaUpdateTrackingAccount> updatedAccounts = new HashMap<>();
//	private final List<Address> deletedAccounts = new ArrayList<>();
//
//	protected HederaAbstractWorldUpdater(final W world) {
//		this.world = world;
//	}
//
//	@Override
//	public Account get(Address address) {
//		if (deletedAccounts.contains(address)) {
//			return null;
//		} else if (updatedAccounts.containsKey(address)) {
//			return updatedAccounts.get(address).getWrappedAccount();
//		} else if (createdAccounts.containsKey(address)) {
//			return createdAccounts.get(address);
//		}
//		return world.get(address);
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
//		final Account origin = getForMutation(address);
//		if (origin == null) {
//			return null;
//		} else {
//			return new WrappedEvmAccount(track(new HederaUpdateTrackingAccount(origin)));
//		}
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
//		HederaWorldState.WorldStateAccount created = world.new WorldStateAccount(address, balance);
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
//		Id newContractId = world.newContractId(sponsor);
//		return newContractId.asEvmAddress();
//	}
//
//	@Override
//	public HederaWorldUpdater updater() {
//		return new HederaStackedUpdater(this);
//	}
//
//	@Override
//	public Optional<WorldUpdater> parentUpdater() {
//		if (world instanceof WorldUpdater) {
//			return Optional.of((WorldUpdater) world);
//		} else {
//			return Optional.empty();
//		}
//	}
//
//	@Override
//	public void revert() {
//		for (int i = 0; i < numberOfAddressesClaimed; i++) {
//			world.reclaimContractId();
//		}
//		numberOfAddressesClaimed = 0;
//		createdAccounts.clear();
//		updatedAccounts.clear();
//		deletedAccounts.clear();
//	}
//
//	@Override
//	public Collection<HederaUpdateTrackingAccount> getTouchedAccounts() {
//		return updatedAccounts.values();
//	}
//
//	@Override
//	public Collection<Address> getDeletedAccountAddresses() {
//		return deletedAccounts;
//	}
//
//	public Collection<HederaWorldState.WorldStateAccount> getCreatedAccounts() {
//		return createdAccounts.values();
//	}
//
//	protected abstract HederaUpdateTrackingAccount getForMutation(Address address);
//
//	/**
//	 * The world view on top of which this buffer updates.
//	 *
//	 * @return The world view on top of which this buffer updates.
//	 */
//	protected W wrappedWorldView() {
//		return world;
//	}
//}