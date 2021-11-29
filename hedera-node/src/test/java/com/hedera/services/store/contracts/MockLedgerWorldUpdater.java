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
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

public class MockLedgerWorldUpdater
		extends AbstractLedgerWorldUpdater<HederaWorldState, HederaWorldState.WorldStateAccount> {

	public MockLedgerWorldUpdater(final HederaWorldState world, final WorldLedgers trackingLedgers) {
		super(world, trackingLedgers);
	}

	@Override
	protected HederaWorldState.WorldStateAccount getForMutation(Address address) {
		return wrappedWorldView().get(address);
	}

	@Override
	public void commit() {
		trackingLedgers().commit();
	}

	@Override
	public WorldUpdater updater() {
		return new MockStackedLedgerUpdater(this, trackingLedgers().wrapped());
	}
}
