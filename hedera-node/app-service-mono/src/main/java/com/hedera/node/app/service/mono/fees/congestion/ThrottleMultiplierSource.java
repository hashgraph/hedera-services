/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.node.app.service.mono.fees.congestion;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.hapi.utils.throttles.CongestibleThrottle;
import com.hedera.node.app.service.mono.fees.calculation.CongestionMultipliers;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.Logger;

public class ThrottleMultiplierSource implements FeeMultiplierSource {
    private static final long DEFAULT_MULTIPLIER = 1L;
    private static final Instant[] NO_CONGESTION_STARTS = new Instant[0];
    private static final CongestionMultipliers NO_CONFIG = null;

    private long multiplier = DEFAULT_MULTIPLIER;
    private long previousMultiplier = DEFAULT_MULTIPLIER;
    private long[][] activeTriggerValues = {};
    private Instant[] congestionLevelStarts = NO_CONGESTION_STARTS;
    private CongestionMultipliers activeConfig = NO_CONFIG;

    private final String usageType;
    private final String abbrevUsageType;
    private final String congestionType;
    private final Logger log;
    private final LongSupplier minCongestionPeriodSupplier;
    private final Supplier<CongestionMultipliers> multiplierSupplier;
    private final Supplier<List<? extends CongestibleThrottle>> throttleSource;
    private List<? extends CongestibleThrottle> activeThrottles = Collections.emptyList();

    public ThrottleMultiplierSource(
            final String usageType,
            final String abbrevUsageType,
            final String congestionType,
            final Logger log,
            final LongSupplier minCongestionPeriodSupplier,
            final Supplier<CongestionMultipliers> multiplierSupplier,
            final Supplier<List<? extends CongestibleThrottle>> throttleSource) {
        this.abbrevUsageType = abbrevUsageType;
        this.log = log;
        this.minCongestionPeriodSupplier = minCongestionPeriodSupplier;
        this.multiplierSupplier = multiplierSupplier;
        this.throttleSource = throttleSource;
        this.congestionType = congestionType;
        this.usageType = usageType;
    }

    @Override
    public void updateMultiplier(final TxnAccessor accessor, final Instant consensusNow) {
        if (accessor.congestionExempt()) {
            return;
        }

        if (ensureConfigUpToDate()) {
            rebuildState();
        }

        long x = maxMultiplierOfActiveConfig(activeConfig.multipliers());
        updateCongestionLevelStartsWith(activeConfig.multipliers(), x, consensusNow);
        long minPeriod = minCongestionPeriodSupplier.getAsLong();
        multiplier = highestMultiplierNotShorterThan(activeConfig.multipliers(), minPeriod, consensusNow);

        if (multiplier != previousMultiplier) {
            logMultiplierChange(previousMultiplier, multiplier);
        }
        previousMultiplier = multiplier;
    }

    @Override
    public long currentMultiplier(final TxnAccessor accessor) {
        if (accessor.congestionExempt()) {
            return DEFAULT_MULTIPLIER;
        }
        return multiplier;
    }

    @Override
    public void resetExpectations() {
        activeThrottles = throttleSource.get();
        if (activeThrottles.isEmpty()) {
            log.warn(
                    "Throttle multiplier for {} congestion has no throttle buckets, "
                            + "fee multiplier will remain at one!",
                    congestionType);
        }
        ensureConfigUpToDate();
        rebuildState();
    }

    @Override
    public void resetCongestionLevelStarts(final Instant[] savedStartTimes) {
        congestionLevelStarts = savedStartTimes.clone();
    }

    @Override
    public Instant[] congestionLevelStarts() {
        /* If the Platform is serializing a fast-copy of the MerkleNetworkContext,
        and that copy references this object's congestionLevelStarts, we will get
        a (transient) ISS if the congestion level changes mid-serialization on one
        node but not others. */
        return congestionLevelStarts.clone();
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
            sb.append("\n  (")
                    .append(throttle.name())
                    .append(") When ")
                    .append(usageType)
                    .append(" exceeds:\n");
            for (int j = 0; j < multipliers.length; j++) {
                sb.append("    ")
                        .append(readableTpsCutoffFor(activeTriggerValues[i][j], throttle.mtps(), throttle.capacity()))
                        .append(" ")
                        .append(abbrevUsageType)
                        .append(", multiplier is ")
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

    private boolean ensureConfigUpToDate() {
        var currConfig = multiplierSupplier.get();
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

        logReadableCutoffs();
    }

    private long maxMultiplierOfActiveConfig(final long[] multipliers) {
        long max = DEFAULT_MULTIPLIER;
        for (int i = 0; i < activeTriggerValues.length; i++) {
            long used = activeThrottles.get(i).used();
            for (int j = 0; j < multipliers.length; j++) {
                if (used >= activeTriggerValues[i][j]) {
                    max = Math.max(max, multipliers[j]);
                }
            }
        }
        return max;
    }

    /* Use the highest multiplier whose congestion level we have
    stayed above for at least the minimum number of seconds. */
    private long highestMultiplierNotShorterThan(
            final long[] multipliers, final long period, final Instant consensusNow) {
        multiplier = DEFAULT_MULTIPLIER;
        for (int i = multipliers.length - 1; i >= 0; i--) {
            var levelStart = congestionLevelStarts[i];
            if (levelStart != null) {
                long secsAtLevel = Duration.between(levelStart, consensusNow).getSeconds();
                if (secsAtLevel >= period) {
                    multiplier = multipliers[i];
                    break;
                }
            }
        }
        return multiplier;
    }

    private void updateCongestionLevelStartsWith(final long[] multipliers, long x, Instant consensusNow) {
        for (int i = 0; i < multipliers.length; i++) {
            if (x < multipliers[i]) {
                congestionLevelStarts[i] = null;
            } else if (congestionLevelStarts[i] == null) {
                congestionLevelStarts[i] = consensusNow;
            }
        }
    }

    @VisibleForTesting
    public void logReadableCutoffs() {
        log.info("The new cutoffs for {} congestion pricing are : {}", congestionType, this);
    }

    @VisibleForTesting
    public void logMultiplierChange(long prev, long cur) {
        if (prev == DEFAULT_MULTIPLIER) {
            log.info("Congestion pricing beginning w/ {}x multiplier", cur);
        } else {
            if (cur > prev) {
                log.info("Congestion pricing continuing, reached {}x multiplier", cur);
            } else if (cur == DEFAULT_MULTIPLIER) {
                log.info("Congestion pricing ended");
            }
        }
    }
}
