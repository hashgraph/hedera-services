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

package com.swirlds.merkle.map;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldMain;

/**
 * Singleton factory for loading and registering {@link MerkleMap} statistics. This is the primary entry point for all
 * {@link SwirldMain} implementations that wish to track {@link MerkleMap} statistics.
 */
public final class MerkleMapMetrics {

    private static final String MM_CATEGORY = "MM";

    /**
     * true if these statistics have been registered by the application; otherwise false
     */
    private static volatile boolean registered;

    // avg time taken to execute the MerkleMap get method (in microseconds)
    private static final RunningAverageMetric.Config MMM_GET_MICRO_SEC_CONFIG = new RunningAverageMetric.Config(
                    MM_CATEGORY, "mmGetMicroSec")
            .withDescription("avg time taken to execute the MerkleMap get method (in microseconds)");
    private static RunningAverageMetric mmmGetMicroSec;

    // avg time taken to execute the MerkleMap getForModify method (in microseconds)
    private static final RunningAverageMetric.Config MM_GFM_MICRO_SEC_CONFIG = new RunningAverageMetric.Config(
                    MM_CATEGORY, "mmGfmMicroSec")
            .withDescription("avg time taken to execute the MerkleMap getForModify method (in microseconds)");
    private static RunningAverageMetric mmGfmMicroSec;

    // avg time taken to execute the MerkleMap replace method (in microseconds)
    private static final RunningAverageMetric.Config MM_REPLACE_MICRO_SEC_CONFIG = new RunningAverageMetric.Config(
                    MM_CATEGORY, "mmReplaceMicroSec")
            .withDescription("avg time taken to execute the MerkleMap replace method (in microseconds)");
    private static RunningAverageMetric mmReplaceMicroSec;

    // avg time taken to execute the MerkleMap put method (in microseconds)
    private static final RunningAverageMetric.Config MM_PUT_MICRO_SEC_CONFIG = new RunningAverageMetric.Config(
                    MM_CATEGORY, "mmPutMicroSec")
            .withDescription("avg time taken to execute the MerkleMap put method (in microseconds)");
    private static RunningAverageMetric mmPutMicroSec;

    /**
     * Default private constructor to ensure that this may not be instantiated.
     */
    private MerkleMapMetrics() {}

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
     * Registers the {@link MerkleMap} statistics with the specified {@link Platform} instance.
     *
     * @param metrics
     * 		the metrics-system
     */
    public static void register(final Metrics metrics) {
        mmmGetMicroSec = metrics.getOrCreate(MMM_GET_MICRO_SEC_CONFIG);
        mmGfmMicroSec = metrics.getOrCreate(MM_GFM_MICRO_SEC_CONFIG);
        mmReplaceMicroSec = metrics.getOrCreate(MM_REPLACE_MICRO_SEC_CONFIG);
        mmPutMicroSec = metrics.getOrCreate(MM_PUT_MICRO_SEC_CONFIG);

        registered = true;
    }

    /**
     * Update avg time taken to execute the MerkleMap get method
     *
     * @param microseconds the value that should be recorded
     */
    public static void updateMmmGetMicroSec(final long microseconds) {
        mmmGetMicroSec.update(microseconds);
    }

    /**
     * Update avg time taken to execute the MerkleMap getForModify method
     *
     * @param microseconds the value that should be recorded
     */
    public static void updateMmGfmMicroSec(final long microseconds) {
        mmGfmMicroSec.update(microseconds);
    }

    /**
     * Update avg time taken to execute the MerkleMap put method
     *
     * @param microseconds the value that should be recorded
     */
    public static void updateMmReplaceMicroSec(final long microseconds) {
        mmReplaceMicroSec.update(microseconds);
    }

    /**
     * Update avg time taken to execute the MerkleMap put method
     *
     * @param microseconds the value that should be recorded
     */
    public static void updateMmPutMicroSec(final long microseconds) {
        mmPutMicroSec.update(microseconds);
    }
}
