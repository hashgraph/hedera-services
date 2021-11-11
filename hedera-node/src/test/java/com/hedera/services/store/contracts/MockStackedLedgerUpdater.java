package com.hedera.services.store.contracts;

import org.hyperledger.besu.evm.worldstate.WorldUpdater;

public class MockStackedLedgerUpdater
		extends AbstractStackedLedgerUpdater<HederaWorldState, HederaWorldState.WorldStateAccount> {

	public MockStackedLedgerUpdater(
			final AbstractLedgerWorldUpdater<HederaWorldState, HederaWorldState.WorldStateAccount> world,
			final WorldLedgers trackingLedgers
	) {
		super(world, trackingLedgers);
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public WorldUpdater updater() {
		return new MockStackedLedgerUpdater((AbstractLedgerWorldUpdater) this, trackingLedgers().wrapped());
	}
}
