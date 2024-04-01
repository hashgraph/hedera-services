/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.metric;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.StatsConfig;
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.RunningAverageMetric.Config;
import com.swirlds.metrics.api.IntegerAccumulator;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BinaryOperator;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A class to handle the metrics for the handle-workflow
 */
@Singleton
public class HandleWorkflowMetrics {

    private static final BinaryOperator<Integer> AVERAGE = (sum, count) -> count == 0 ? 0 : sum / count;

    private static final Config GAS_PER_CONS_SEC_CONFIG = new Config("app", "gasPerConsSec")
            .withDescription("average EVM gas used per second of consensus time")
            .withFormat("%,13.6f");

    private final Map<HederaFunctionality, TransactionMetric> transactionMetrics =
            new EnumMap<>(HederaFunctionality.class);

    private final RunningAverageMetric gasPerConsSec;

    private long gasUsedThisConsensusSecond = 0L;

    /**
     * Constructor for the HandleWorkflowMetrics
     *
     * @param metrics the {@link Metrics} object where all metrics will be registered
     */
    @Inject
    public HandleWorkflowMetrics(@NonNull final Metrics metrics, @NonNull final ConfigProvider configProvider) {
        requireNonNull(metrics, "metrics must not be null");
        requireNonNull(configProvider, "configProvider must not be null");

        for (final var functionality : HederaFunctionality.values()) {
            if (functionality == HederaFunctionality.NONE) {
                continue;
            }
            final var protoName = functionality.protoName();
            final var name = protoName.substring(0, 1).toLowerCase() + protoName.substring(1);
            final var maxConfig = new IntegerAccumulator.Config("app", name + "DurationMax")
                    .withDescription("The maximum duration of a " + name + " transaction in nanoseconds")
                    .withUnit("ns");
            final var maxMetric = metrics.getOrCreate(maxConfig);
            final var avgConfig = new IntegerPairAccumulator.Config<>(
                            "app", name + "DurationAvg", Integer.class, AVERAGE)
                    .withDescription("The average duration of a " + name + " transaction in nanoseconds")
                    .withUnit("ns");
            final var avgMetric = metrics.getOrCreate(avgConfig);
            transactionMetrics.put(functionality, new TransactionMetric(maxMetric, avgMetric));
        }

        final StatsConfig statsConfig = configProvider.getConfiguration().getConfigData(StatsConfig.class);
        gasPerConsSec = metrics.getOrCreate(GAS_PER_CONS_SEC_CONFIG.withHalfLife(statsConfig.runningAvgHalfLifeSecs()));
    }

    /**
     * Update the metrics for the given functionality
     *
     * @param functionality the {@link HederaFunctionality} for which the metrics will be updated
     * @param duration the duration of the transaction in {@code ns}
     */
    public void updateTransactionDuration(@NonNull final HederaFunctionality functionality, final int duration) {
        requireNonNull(functionality, "functionality must not be null");
        final var metric = transactionMetrics.get(functionality);
        if (metric != null) {
            // We do not synchronize the update of the metrics. This may lead to a situation where the max value is
            // is stored in one reporting interval and the average in another. This is acceptable as synchronizing
            // the updates would introduce a severe performance penalty.
            metric.max.update(duration);
            metric.avg.update(duration, 1);
        }
    }

    public void switchConsensusSecond() {
        gasPerConsSec.update(gasUsedThisConsensusSecond);
        gasUsedThisConsensusSecond = 0L;
    }

    public void addGasUsed(final long gasUsed) {
        gasUsedThisConsensusSecond += gasUsed;
    }

    private record TransactionMetric(IntegerAccumulator max, IntegerPairAccumulator<Integer> avg) {}
}
