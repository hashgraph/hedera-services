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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.LinkedHashMap;
import java.util.Map;

public class HederaStackedWorldStateUpdater
		extends AbstractStackedLedgerUpdater<HederaMutableWorldState, HederaWorldState.WorldStateAccount>
		implements HederaWorldUpdater {

	private final Map<Address, Address> sponsorMap = new LinkedHashMap<>();
	private final HederaMutableWorldState worldState;

	private Gas sbhRefund = Gas.ZERO;

	public HederaStackedWorldStateUpdater(
			final AbstractLedgerWorldUpdater<HederaMutableWorldState, HederaWorldState.WorldStateAccount> updater,
			final HederaMutableWorldState worldState,
			final WorldLedgers trackingLedgers
	) {
		super(updater, trackingLedgers);
		this.worldState = worldState;
	}

	@Override
	public Address allocateNewContractAddress(final Address sponsor) {
		Address newAddress = worldState.newContractAddress(sponsor);
		sponsorMap.put(newAddress, sponsor);
		return newAddress;
	}

	public Map<Address, Address> getSponsorMap() {
		return sponsorMap;
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
		for (int i = 0; i < sponsorMap.size(); i++) {
			worldState.reclaimContractId();
		}
		sponsorMap.clear();
		sbhRefund = Gas.ZERO;
		super.revert();
	}

	@Override
	public void commit() {
		((HederaWorldUpdater) wrappedWorldView()).getSponsorMap().putAll(sponsorMap);
		((HederaWorldUpdater) wrappedWorldView()).addSbhRefund(sbhRefund);
		sbhRefund = Gas.ZERO;
		super.commit();
	}

	@Override
	public HederaWorldState.WorldStateAccount getHederaAccount(final Address address) {
		return parentUpdater().map(u -> ((HederaWorldUpdater) u).getHederaAccount(address)).orElse(null);
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public WorldUpdater updater() {
		return new HederaStackedWorldStateUpdater(
				(AbstractLedgerWorldUpdater) this,
				worldState,
				trackingLedgers().wrapped());
	}
}
