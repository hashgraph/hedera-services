/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.fcqueue;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.utility.CommonUtils;

/**
 * Singleton factory for loading and registering {@link FCQueue} statistics. This is the primary entry point for all
 * {@link SwirldMain} implementations that wish to track {@link FCQueue} statistics.
 */
public class FCQueueStatistics {

    public static final String FCQUEUE_CATEGORY = "FCQueue";

    /**
     * true if these statistics have been registered by the application; otherwise false
     */
    private static volatile boolean registered;

    /**
     * avg time taken to execute the FCQueue add method, including locks (in microseconds)
     */
    private static final RunningAverageMetric.Config FCQ_ADD_EXECUTION_MICROS_CONFIG = new RunningAverageMetric.Config(
                    FCQUEUE_CATEGORY, "fcqAddExecMicroSec")
            .withDescription("avg time taken to execute the FCQueue add method, not including locks (in microseconds)")
            .withFormat(FORMAT_9_6);

    private static RunningAverageMetric fcqAddExecutionMicros;

    /**
     * avg time taken to execute the FCQueue remove method, including locks (in microseconds)
     */
    private static final RunningAverageMetric.Config FCQ_REMOVE_EXECUTION_MICROS_CONFIG =
            new RunningAverageMetric.Config(FCQUEUE_CATEGORY, "fcqRemoveExecMicroSec")
                    .withDescription(
                            "avg time taken to execute the FCQueue remove method, not including locks (in microseconds)")
                    .withFormat(FORMAT_9_6);

    private static RunningAverageMetric fcqRemoveExecutionMicros;

    /**
     * avg time taken to execute the FCQueue getHash method, including locks (in microseconds)
     */
    private static final RunningAverageMetric.Config FCQ_HASH_EXECUTION_MICROS_CONFIG = new RunningAverageMetric.Config(
                    FCQUEUE_CATEGORY, "fcqHashExecMicroSec")
            .withDescription(
                    "avg time taken to execute the FCQueue remove method, not including locks (in microseconds)")
            .withFormat(FORMAT_9_6);

    private static RunningAverageMetric fcqHashExecutionMicros;

    /**
     * Default private constructor to ensure that this may not be instantiated.
     */
    private FCQueueStatistics() {}

    /**
     * Registers the {@link FCQueue} statistics with the specified {@link Platform} instance.
     *
     * @param metrics
     * 		the metrics-system
     */
    public static void register(final Metrics metrics) {
        CommonUtils.throwArgNull(metrics, "metrics");
        fcqAddExecutionMicros = metrics.getOrCreate(FCQ_ADD_EXECUTION_MICROS_CONFIG);
        fcqRemoveExecutionMicros = metrics.getOrCreate(FCQ_REMOVE_EXECUTION_MICROS_CONFIG);
        fcqHashExecutionMicros = metrics.getOrCreate(FCQ_HASH_EXECUTION_MICROS_CONFIG);

        registered = true;
    }

    /**
     * Gets a value indicating whether the {@link SwirldMain} has called the {@link
     * #register(Metrics)} method on this factory.
     *
     * @return true if these statistics have been registered by the application; otherwise false
     */
    public static boolean isRegistered() {
        return registered;
    }

    /**
     * Update the average time taken to execute the FCQueue add() method
     *
     * @param value the value to record
     */
    public static void updateFcqAddExecutionMicros(final double value) {
        if (fcqAddExecutionMicros != null) {
            fcqAddExecutionMicros.update(value);
        }
    }

    /**
     * Update the average time taken to execute the FCQueue remove() method
     *
     * @param value the value to record
     */
    public static void updateFcqRemoveExecutionMicros(final double value) {
        if (fcqRemoveExecutionMicros != null) {
            fcqRemoveExecutionMicros.update(value);
        }
    }

    /**
     * Update the average time taken to execute the FCQueue getHash() method
     *
     * @param value the value to record
     */
    public static void updateFcqHashExecutionMicros(final double value) {
        if (fcqHashExecutionMicros != null) {
            fcqHashExecutionMicros.update(value);
        }
    }
}
