package com.hedera.services.fees;

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

import com.hedera.services.context.SingletonContextsManager;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.CongestionMultipliers;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;

public class TxnRateFeeMultiplierSource implements FeeMultiplierSource {
	private static final Logger log = LogManager.getLogger(TxnRateFeeMultiplierSource.class);

	private static final long DEFAULT_MULTIPLIER = 1L;
	private static final Instant[] NO_CONGESTION_STARTS = new Instant[0];
	private static final CongestionMultipliers NO_CONFIG = null;

	private final GlobalDynamicProperties properties;
	private final FunctionalityThrottling throttling;

	private long multiplier = DEFAULT_MULTIPLIER;
	private long previousMultiplier = DEFAULT_MULTIPLIER;
	private long[][] activeTriggerValues = {};
	private Instant[] congestionLevelStarts = NO_CONGESTION_STARTS;
	private CongestionMultipliers activeConfig = NO_CONFIG;

	private List<DeterministicThrottle> activeThrottles = Collections.emptyList();

	public TxnRateFeeMultiplierSource(GlobalDynamicProperties properties, FunctionalityThrottling throttling) {
		this.properties = properties;
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
			log.warn("CryptoTransfer has no throttle buckets, fee multiplier will remain at one!");
		}
		ensureConfigUpToDate();
		rebuildState();
	}

	@Override
	public String toString() {
		if (activeConfig == NO_CONFIG) {
			return " <N/A>";
		}
		var sb = new StringBuilder();
		long[] multipliers = activeConfig.multipliers();
		for (int i = 0, n = activeThrottles.size(); i < n; i++) {
			var throttle = activeThrottles.get(i);
			sb.append("\n  (").append(throttle.name()).append(") When logical TPS exceeds:\n");
			for (int j = 0; j < multipliers.length; j++) {
				sb.append("    ")
						.append(readableTpsCutoffFor(activeTriggerValues[i][j], throttle.mtps(), throttle.capacity()))
						.append(" TPS, multiplier is ")
						.append(multipliers[j])
						.append("x")
						.append((j == multipliers.length - 1) ? "" : "\n");
			}
		}
		return sb.toString();
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
	public void updateMultiplier(Instant consensusNow) {
		if (ensureConfigUpToDate()) {
			rebuildState();
		}

		long x = DEFAULT_MULTIPLIER;
		long[] multipliers = activeConfig.multipliers();
		for (int i = 0; i < activeTriggerValues.length; i++) {
			long used = activeThrottles.get(i).used();
			for (int j = 0; j < multipliers.length; j++) {
				if (used >= activeTriggerValues[i][j]) {
					x = Math.max(x, multipliers[j]);
				}
			}
		}
		for (int i = 0; i < multipliers.length; i++) {
			if (x < multipliers[i]) {
				congestionLevelStarts[i] = null;
			} else if (congestionLevelStarts[i] == null) {
				congestionLevelStarts[i] = consensusNow;
			}
		}

		/* Use the highest multiplier whose congestion level we have
		stayed above for at least the minimum number of seconds. */
		long minPeriod = properties.feesMinCongestionPeriod();
		multiplier = DEFAULT_MULTIPLIER;
		for (int i = multipliers.length - 1; i >= 0; i--) {
			var levelStart = congestionLevelStarts[i];
			if (levelStart != null) {
				long secsAtLevel = Duration.between(levelStart, consensusNow).getSeconds();
				if (secsAtLevel >= minPeriod) {
					multiplier = multipliers[i];
					break;
				}
			}
		}

		if (multiplier != previousMultiplier) {
			logMultiplierChange(previousMultiplier, multiplier, log);
		}
		previousMultiplier = multiplier;
	}

	@Override
	public void resetCongestionLevelStarts(Instant[] savedStartTimes) {
		congestionLevelStarts = savedStartTimes;
	}

	@Override
	public Instant[] congestionLevelStarts() {
		return congestionLevelStarts;
	}

	private boolean ensureConfigUpToDate() {
		var currConfig = properties.congestionMultipliers();
		if (!currConfig.equals(activeConfig)) {
			activeConfig = currConfig;
			return true;
		}
		return false;
	}

	private void rebuildState() {
		int n = activeThrottles.size();
		int[] triggers = activeConfig.usagePercentTriggers();
		long[] multipliers = activeConfig.multipliers();
		activeTriggerValues = new long[n][multipliers.length];
		for (int i = 0; i < n; i++) {
			var throttle = activeThrottles.get(i);
			long capacity = throttle.capacity();
			for (int j = 0; j < triggers.length; j++) {
				long cutoff = (capacity / 100L) * triggers[j];
				activeTriggerValues[i][j] = cutoff;
			}
		}

		congestionLevelStarts = new Instant[multipliers.length];

		logReadableCutoffs(log);
	}

	void logMultiplierChange(long prev, long cur, Logger refinedLog) {
		if (prev == DEFAULT_MULTIPLIER)	{
			refinedLog.info("Congestion pricing beginning w/ " + cur + "x multiplier");
		} else {
			if (cur > prev) {
				refinedLog.info("Congestion pricing continuing, reached " + cur + "x multiplier");
			} else if (cur == DEFAULT_MULTIPLIER) {
				refinedLog.info("Congestion pricing ended");
			}
		}
	}

	void logReadableCutoffs(Logger refinedLog) {
		refinedLog.info("The new cutoffs for congestion pricing are:" + this);
	}
}
