/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.metrics;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_6;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_16_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.system.PlatformStatNames;
import com.swirlds.common.system.SwirldState;
import com.swirlds.platform.stats.AverageTimeStat;
import java.time.temporal.ChronoUnit;

/**
 * Collection of metrics related to SwirldState
 */
public class SwirldStateMetrics {

    private final RunningAverageMetric avgSecTransHandled;

    private final RunningAverageMetric avgConsHandleTime;

    private final SpeedometerMetric transHandledPerSecond;

    private final RunningAverageMetric avgStateCopyMicros;
    /**
     * average time spent in
     * {@code SwirldStateManager#prehandle} by the {@code intake} thread (in microseconds)
     */
    private final AverageTimeStat preHandleTime;

    private final AverageTimeStat preConsHandleTime;

    /**
     * Constructor of {@code SwirldStateMetrics}
     *
     * @param metricsConfig
     *      configuration for metrics
     * @param metrics
     * 		a reference to the metrics-system
     * @throws IllegalArgumentException
     * 		if {@code metrics} is {@code null}
     */
    public SwirldStateMetrics(final MetricsConfig metricsConfig, final Metrics metrics) {
        avgSecTransHandled = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                INTERNAL_CATEGORY, "secTransH")
                .withDescription(
                        "avg time to handle a consensus transaction in SwirldState.handleTransaction " + "(in seconds)")
                .withFormat(FORMAT_10_6));
        avgConsHandleTime = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                PLATFORM_CATEGORY, "SecC2H")
                .withDescription("time from knowing consensus for a transaction to handling it (in seconds)")
                .withFormat(FORMAT_10_3));
        transHandledPerSecond = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig,
                INTERNAL_CATEGORY, PlatformStatNames.TRANSACTIONS_HANDLED_PER_SECOND)
                .withDescription(
                        "number of consensus transactions per second handled " + "by SwirldState.handleTransaction()")
                .withFormat(FORMAT_9_6));
        avgStateCopyMicros = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                INTERNAL_CATEGORY, "stateCopyMicros")
                .withDescription("average time it takes the SwirldState.copy() method in SwirldState to finish "
                        + "(in microseconds)")
                .withFormat(FORMAT_16_2));
        preConsHandleTime = new AverageTimeStat(metricsConfig,
                metrics,
                ChronoUnit.MICROS,
                INTERNAL_CATEGORY,
                "preConsHandleMicros",
                "average time it takes to handle a pre-consensus event from q4 (in microseconds)");
        preHandleTime = new AverageTimeStat(metricsConfig,
                metrics,
                ChronoUnit.MICROS,
                INTERNAL_CATEGORY,
                "preHandleMicros",
                "average time it takes to perform preHandle (in microseconds)");
    }

    /**
     * Records the amount of time to handle a consensus transaction in {@link SwirldState}.
     *
     * @param seconds
     * 		the amount of time in seconds
     */
    public void consensusTransHandleTime(final double seconds) {
        avgSecTransHandled.update(seconds);
    }

    /**
     * Records the amount of time between a transaction reaching consensus and being handled in {@link SwirldState}.
     *
     * @param seconds
     * 		the amount of time in seconds
     */
    public void consensusToHandleTime(final double seconds) {
        avgConsHandleTime.update(seconds);
    }

    /**
     * Records the fact that consensus transactions were handled by {@link SwirldState}.
     */
    public void consensusTransHandled(final int numTrans) {
        transHandledPerSecond.update(numTrans);
    }

    /**
     * Records the time it takes {@link SwirldState#copy()} to finish (in microseconds)
     *
     * @param micros
     * 		the amount of time in microseconds
     */
    public void stateCopyMicros(final double micros) {
        avgStateCopyMicros.update(micros);
    }

    /**
     * The amount of time it takes to handle a single event from the pre-consensus event queue.
     */
    public void preConsensusHandleTime(final long start, final long end) {
        preConsHandleTime.update(start, end);
    }

    /**
     * The amount of time it takes to apply an event or a transaction, depending on which {@link SwirldState} the
     * application implements.
     */
    public void preHandleTime(final long start, final long end) {
        preHandleTime.update(start, end);
    }
}
