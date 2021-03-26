package com.hedera.services.fees;

import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;

public class TxnRateFeeMultiplierSource implements FeeMultiplierSource {
	private static final Logger log = LogManager.getLogger(TxnRateFeeMultiplierSource.class);

	private static final long DEFAULT_MULTIPLIER = 1L;

	private static final int[] UPSCALE_USAGE_PERCENT_TRIGGERS = { 90, 95, 99 };
	private static final long[] UPSCALE_MULTIPLIERS = { 10L, 25L, 100L };

	private final FunctionalityThrottling throttling;
	private long multiplier = DEFAULT_MULTIPLIER;
	private long[][] activeTriggerValues = {};
	private List<DeterministicThrottle> activeThrottles = Collections.emptyList();

	public TxnRateFeeMultiplierSource(FunctionalityThrottling throttling) {
		this.throttling = throttling;
	}

	@Override
	public long currentMultiplier() {
		return multiplier;
	}

	@Override
	public void resetExpectations() {
		activeThrottles = throttling.activeThrottlesFor(CryptoTransfer);
		if (activeThrottles.isEmpty()) {
			log.info("Normally I wouldn't let this slide!");
		}

		int n = activeThrottles.size();
		activeTriggerValues = new long[n][UPSCALE_MULTIPLIERS.length];
		for (int i = 0; i < n; i++) {
			var throttle = activeThrottles.get(i);
			long capacity = throttle.capacity();
			for (int j = 0; j < UPSCALE_USAGE_PERCENT_TRIGGERS.length; j++) {
				long cutoff = (capacity / 100L) * UPSCALE_USAGE_PERCENT_TRIGGERS[j];
				activeTriggerValues[i][j] = cutoff;
			}
		}
		logReadableCutoffs(log);
	}

	void logReadableCutoffs(Logger refinedLog) {
		refinedLog.info("The new cutoffs for congestion pricing are:\n  " + this);
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		for (int i = 0, n = activeThrottles.size(); i < n; i++) {
			var throttle = activeThrottles.get(i);
			sb.append("  (").append(throttle.name()).append(") When logical TPS exceeds:\n");
			for (int j = 0; j < UPSCALE_MULTIPLIERS.length; j++) {
				sb.append("    ")
						.append(readableTpsCutoffFor(activeTriggerValues[i][j], throttle.mtps(), throttle.capacity()))
						.append(" TPS, multiplier is ")
						.append(UPSCALE_MULTIPLIERS[j])
						.append("x\n");
			}
		}
		return sb.toString().trim();
	}

	private String readableTpsCutoffFor(long capacityCutoff, long mtps, long capacity) {
		return String.format("%.2f", (capacityCutoff * 1.0) / capacity * mtps / 1000.0);
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
	@Override
	public void updateMultiplier() {
		multiplier = DEFAULT_MULTIPLIER;
		for (int i = 0; i < activeTriggerValues.length; i++) {
			long used = activeThrottles.get(i).used();
			for (int j = 0; j < UPSCALE_MULTIPLIERS.length && used >= activeTriggerValues[i][j]; j++) {
				multiplier = Math.max(multiplier, UPSCALE_MULTIPLIERS[j]);
			}
		}
	}
}
