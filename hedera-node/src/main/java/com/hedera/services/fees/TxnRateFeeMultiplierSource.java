package com.hedera.services.fees;

import com.hedera.services.throttling.DeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;

public class TxnRateFeeMultiplierSource implements FeeMultiplierSource {
	private static final long DEFAULT_MULTIPLIER = 1L;
	private static final long PERCENT_FOR_FULL_CAPACITY = 100L;

	private final FunctionalityThrottling throttling;

	private long multiplier = DEFAULT_MULTIPLIER;

	public TxnRateFeeMultiplierSource(FunctionalityThrottling throttling) {
		this.throttling = throttling;
	}

	@Override
	public long currentMultiplier() {
		return multiplier;
	}

	/**
	 * Called by {@code handleTransaction} when the multiplier should be recomputed.
	 *
	 * It is <b>crucial</b> that every node in the network reach the same conclusion
	 * for the multiplier. Thus the {@link FunctionalityThrottling} implementation
	 * must provide a list of throttle state snapshots that reflect the consensus
	 * of the entire network.
	 *
	 * That is, the throttle states must be a child of the {@link com.hedera.services.ServicesState}.
	 */
	public void updateMultiplier() {
		multiplier = DEFAULT_MULTIPLIER;
		var throttleStates = throttling.throttleStatesFor(CryptoTransfer);
		for (var throttleState : throttleStates) {
			multiplier = Math.max(multiplier, multiplierGiven(throttleState));
		}
	}

	private long multiplierGiven(DeterministicThrottle.StateSnapshot throttleState) {
		long wholePercentUsed = (throttleState.getUsed() * PERCENT_FOR_FULL_CAPACITY) / throttleState.getCapacity();
		if (wholePercentUsed < 90) {
			return 1L;
		} else if (wholePercentUsed < 95) {
			return 10L;
		} else if (wholePercentUsed < 99) {
			return 25L;
		} else {
			return 100L;
		}
	}
}
