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
import com.swirlds.common.system.PlatformStatNames;
import com.swirlds.common.system.SwirldState;
import com.swirlds.platform.stats.AverageTimeStat;
import java.time.temporal.ChronoUnit;

/**
 * Collection of metrics related to SwirldState
 */
public class SwirldStateMetrics {

    private static final RunningAverageMetric.Config AVG_SEC_TRANS_HANDLED_CONFIG = new RunningAverageMetric.Config(
                    INTERNAL_CATEGORY, "secTransH")
            .withDescription(
                    "avg time to handle a consensus transaction in SwirldState.handleTransaction " + "(in seconds)")
            .withFormat(FORMAT_10_6);
    private final RunningAverageMetric avgSecTransHandled;

    private static final RunningAverageMetric.Config AVG_CONS_HANDLE_TIME_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "SecC2H")
            .withDescription("time from knowing consensus for a transaction to handling it (in seconds)")
            .withFormat(FORMAT_10_3);
    private final RunningAverageMetric avgConsHandleTime;

    private static final SpeedometerMetric.Config TRANS_HANDLED_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    INTERNAL_CATEGORY, PlatformStatNames.TRANSACTIONS_HANDLED_PER_SECOND)
            .withDescription(
                    "number of consensus transactions per second handled " + "by SwirldState.handleTransaction()")
            .withFormat(FORMAT_9_6);
    private final SpeedometerMetric transHandledPerSecond;

    private static final RunningAverageMetric.Config AVG_STATE_COPY_MICROS_CONFIG = new RunningAverageMetric.Config(
                    INTERNAL_CATEGORY, "stateCopyMicros")
            .withDescription("average time it takes the SwirldState.copy() method in SwirldState to finish "
                    + "(in microseconds)")
            .withFormat(FORMAT_16_2);
    private final RunningAverageMetric avgStateCopyMicros;

    private static final RunningAverageMetric.Config AVG_SHUFFLE_MICROS_CONFIG = new RunningAverageMetric.Config(
                    INTERNAL_CATEGORY, "shuffleMicros")
            .withDescription(
                    "average time spent in SwirldStateManagerSingle.Shuffler#shuffle() method " + "(in microseconds)")
            .withFormat(FORMAT_16_2);
    private final RunningAverageMetric avgShuffleMicros;

    /**
     * average time spent in
     * {@code SwirldStateManager#prehandle} by the {@code intake} thread (in microseconds)
     */
    private final AverageTimeStat preHandleTime;

    private final AverageTimeStat preConsHandleTime;

    /**
     * Constructor of {@code SwirldStateMetrics}
     *
     * @param metrics
     * 		a reference to the metrics-system
     * @throws IllegalArgumentException
     * 		if {@code metrics} is {@code null}
     */
    public SwirldStateMetrics(final Metrics metrics) {
        avgSecTransHandled = metrics.getOrCreate(AVG_SEC_TRANS_HANDLED_CONFIG);
        avgConsHandleTime = metrics.getOrCreate(AVG_CONS_HANDLE_TIME_CONFIG);
        transHandledPerSecond = metrics.getOrCreate(TRANS_HANDLED_PER_SECOND_CONFIG);
        avgStateCopyMicros = metrics.getOrCreate(AVG_STATE_COPY_MICROS_CONFIG);
        avgShuffleMicros = metrics.getOrCreate(AVG_SHUFFLE_MICROS_CONFIG);
        preConsHandleTime = new AverageTimeStat(
                metrics,
                ChronoUnit.MICROS,
                INTERNAL_CATEGORY,
                "preConsHandleMicros",
                "average time it takes to handle a pre-consensus event from q4 (in microseconds)");
        preHandleTime = new AverageTimeStat(
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
     * Records the time spent performing a shuffle in {@link com.swirlds.platform.state.SwirldStateManagerSingle}  (in
     * microseconds).
     *
     * @param micros
     * 		the amount of time in microseconds
     */
    public void shuffleMicros(final double micros) {
        avgShuffleMicros.update(micros);
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
