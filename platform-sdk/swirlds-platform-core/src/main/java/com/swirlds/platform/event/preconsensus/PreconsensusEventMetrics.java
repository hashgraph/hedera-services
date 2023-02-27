/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.preconsensus;

import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.metrics.LongGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;

/**
 * Metrics for preconsensus events.
 */
public class PreconsensusEventMetrics {

    private static final String CATEGORY = "platform";

    private static final LongGauge.Config PRECONSENSUS_EVENT_FILE_COUNT_CONFIG = new LongGauge.Config(
                    CATEGORY, "preconsensusEventFileCount")
            .withDescription("The number of preconsensus event files currently being stored");
    private final LongGauge preconsensusEventFileCount;

    private static final DoubleGauge.Config PRECONSENSUS_EVENT_FILE_AVERAGE_SIZE_MB_CONFIG = new DoubleGauge.Config(
                    CATEGORY, "preconsensusEventFileAverageSizeMB")
            .withDescription("The average size of preconsensus event files, in megabytes.");
    private final DoubleGauge preconsensusEventFileAverageSizeMB;

    private static final DoubleGauge.Config PRECONSENSUS_EVENT_FILE_TOTAL_SIZE_GB_CONFIG = new DoubleGauge.Config(
                    CATEGORY, "preconsensusEventFileTotalSizeGB")
            .withDescription("The total size of all preconsensus event files, in gigabytes.");
    private final DoubleGauge preconsensusEventFileTotalSizeGB;

    private static final SpeedometerMetric.Config PRECONSENSUS_EVENT_FILE_RATE_CONFIG = new SpeedometerMetric.Config(
                    CATEGORY, "preconsensusEventFileRate")
            .withDescription("The number of preconsensus event files written per second.");
    private final SpeedometerMetric preconsensusEventFileRate;

    private static final RunningAverageMetric.Config PRECONSENSUS_EVENT_AVERAGE_FILE_SPAN_CONFIG =
            new RunningAverageMetric.Config(CATEGORY, "preconsensusEventAverageFileSpan")
                    .withDescription("The average generational span of preconsensus event files. Only reflects"
                            + "files written since the last restart.");
    private final RunningAverageMetric preconsensusEventAverageFileSpan;

    private static final RunningAverageMetric.Config PRECONSENSUS_EVENT_AVERAGE_UN_UTILIZED_FILE_SPAN_CONFIG =
            new RunningAverageMetric.Config(CATEGORY, "preconsensusEventAverageUnInitializedFileSpan")
                    .withDescription("The average un-utilized generational span of preconsensus event files. "
                            + "Only reflects files written since the last restart. Smaller is better.");
    private final RunningAverageMetric preconsensusEventAverageUnUtilizedFileSpan;

    private static final LongGauge.Config PRECONSENSUS_EVENT_FILE_OLDEST_GENERATION_CONFIG = new LongGauge.Config(
                    CATEGORY, "preconsensusEventFileOldestGeneration")
            .withDescription("The oldest possible generation that is being " + "stored in preconsensus event files.");
    private final LongGauge preconsensusEventFileOldestGeneration;

    private static final LongGauge.Config PRECONSENSUS_EVENT_FILE_YOUNGEST_GENERATION_CONFIG = new LongGauge.Config(
                    CATEGORY, "preconsensusEventFileYoungestGeneration")
            .withDescription("The youngest possible generation that is being " + "stored in preconsensus event files.");
    private final LongGauge preconsensusEventFileYoungestGeneration;

    private static final LongGauge.Config PRECONSENSUS_EVENT_FILE_OLDEST_SECONDS_CONFIG = new LongGauge.Config(
                    CATEGORY, "preconsensusEventFileOldestSeconds")
            .withDescription("The age of the oldest preconsensus event file, in seconds.");
    private final LongGauge preconsensusEventFileOldestSeconds;

    /**
     * Construct preconsensus event metrics.
     *
     * @param metrics
     * 		the metrics manager for the platform
     */
    public PreconsensusEventMetrics(final Metrics metrics) {
        preconsensusEventFileCount = metrics.getOrCreate(PRECONSENSUS_EVENT_FILE_COUNT_CONFIG);
        preconsensusEventFileAverageSizeMB = metrics.getOrCreate(PRECONSENSUS_EVENT_FILE_AVERAGE_SIZE_MB_CONFIG);
        preconsensusEventFileTotalSizeGB = metrics.getOrCreate(PRECONSENSUS_EVENT_FILE_TOTAL_SIZE_GB_CONFIG);
        preconsensusEventFileRate = metrics.getOrCreate(PRECONSENSUS_EVENT_FILE_RATE_CONFIG);
        preconsensusEventAverageFileSpan = metrics.getOrCreate(PRECONSENSUS_EVENT_AVERAGE_FILE_SPAN_CONFIG);
        preconsensusEventAverageUnUtilizedFileSpan =
                metrics.getOrCreate(PRECONSENSUS_EVENT_AVERAGE_UN_UTILIZED_FILE_SPAN_CONFIG);
        preconsensusEventFileOldestGeneration = metrics.getOrCreate(PRECONSENSUS_EVENT_FILE_OLDEST_GENERATION_CONFIG);
        preconsensusEventFileYoungestGeneration =
                metrics.getOrCreate(PRECONSENSUS_EVENT_FILE_YOUNGEST_GENERATION_CONFIG);
        preconsensusEventFileOldestSeconds = metrics.getOrCreate(PRECONSENSUS_EVENT_FILE_OLDEST_SECONDS_CONFIG);
    }

    /**
     * Get the metric tracking the total number of preconsensus event files currently being tracked.
     */
    public LongGauge getPreconsensusEventFileCount() {
        return preconsensusEventFileCount;
    }

    /**
     * Get the metric tracking the average size of preconsensus event files.
     */
    public DoubleGauge getPreconsensusEventFileAverageSizeMB() {
        return preconsensusEventFileAverageSizeMB;
    }

    /**
     * Get the metric tracking the total size of all preconsensus event files.
     */
    public DoubleGauge getPreconsensusEventFileTotalSizeGB() {
        return preconsensusEventFileTotalSizeGB;
    }

    /**
     * Get the metric tracking the rate at which preconsensus event files are being created.
     */
    public SpeedometerMetric getPreconsensusEventFileRate() {
        return preconsensusEventFileRate;
    }

    /**
     * Get the metric tracking the average generational file span.
     */
    public RunningAverageMetric getPreconsensusEventAverageFileSpan() {
        return preconsensusEventAverageFileSpan;
    }

    /**
     * Get the metric tracking the average un-utilized generational file span.
     */
    public RunningAverageMetric getPreconsensusEventAverageUnUtilizedFileSpan() {
        return preconsensusEventAverageUnUtilizedFileSpan;
    }

    /**
     * Get the metric tracking the oldest possible generation that is being stored in preconsensus event files.
     */
    public LongGauge getPreconsensusEventFileOldestGeneration() {
        return preconsensusEventFileOldestGeneration;
    }

    /**
     * Get the metric tracking the youngest possible generation that is being stored in preconsensus event files.
     */
    public LongGauge getPreconsensusEventFileYoungestGeneration() {
        return preconsensusEventFileYoungestGeneration;
    }

    /**
     * Get the metric tracking the age of the oldest preconsensus event file, in seconds.
     */
    public LongGauge getPreconsensusEventFileOldestSeconds() {
        return preconsensusEventFileOldestSeconds;
    }
}
