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

package com.swirlds.virtualmap.internal.merkle;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_2;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.LongGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.utility.CommonUtils;

/**
 * Encapsulates statistics for a virtual map.
 */
public class VirtualMapStatistics {

    public static final String STAT_CATEGORY = "virtual-map";
    private static final double DEFAULT_HALF_LIFE = 5;

    private final LongGauge.Config sizeConfig;
    private LongGauge size;

    /**
     * The average time to call the VirtualMap flush() method.
     */
    private final RunningAverageMetric.Config flushLatencyConfig;

    private RunningAverageMetric flushLatency;

    /**
     * The average time to call the cache merge() method.
     */
    private final RunningAverageMetric.Config mergeLatencyConfig;

    private RunningAverageMetric mergeLatency;

    /**
     * The average number of virtual root node copies in virtual pipeline flush backlog.
     */
    private final IntegerGauge.Config flushBacklogSizeConfig;

    private IntegerGauge flushBacklogSize;

    /**
     * The total number of virtual map flushes to disk.
     */
    private final Counter.Config flushCounterConfig;

    private Counter flushCounter;

    /**
     * Create a new statistics instance for a virtual map family.
     *
     * @param label
     * 		the label for the virtual map
     * @throws IllegalArgumentException
     * 		if {@code label} is {@code null}
     */
    public VirtualMapStatistics(final String label) {
        CommonUtils.throwArgNull(label, "label");

        sizeConfig = new LongGauge.Config(STAT_CATEGORY, "vMapSize_" + label)
                .withDescription("The size of the VirtualMap '" + label + "'");

        flushLatencyConfig = new RunningAverageMetric.Config(STAT_CATEGORY, "vMapFlushLatency_" + label)
                .withDescription("The flush latency of VirtualMap '" + label + "'")
                .withFormat(FORMAT_10_2)
                .withHalfLife(DEFAULT_HALF_LIFE);

        mergeLatencyConfig = new RunningAverageMetric.Config(STAT_CATEGORY, "vMapMergeLatency_" + label)
                .withDescription("The merge latency of VirtualMap '" + label + "'")
                .withFormat(FORMAT_10_2)
                .withHalfLife(DEFAULT_HALF_LIFE);

        flushBacklogSizeConfig = new IntegerGauge.Config(STAT_CATEGORY, "vMapFlushBacklog_" + label)
                .withDescription("the number of '" + label + "' copies waiting to be flushed");

        flushCounterConfig = new Counter.Config(STAT_CATEGORY, "vMapFlushes_" + label)
                .withDescription("the count of '" + label + "' flushes");
    }

    /**
     * Register all statistics with a registry.
     *
     * @param metrics
     * 		reference to the metrics system
     * @throws IllegalArgumentException
     * 		if {@code metrics} is {@code null}
     */
    public void registerMetrics(final Metrics metrics) {
        CommonUtils.throwArgNull(metrics, "metrics");
        size = metrics.getOrCreate(sizeConfig);
        flushLatency = metrics.getOrCreate(flushLatencyConfig);
        mergeLatency = metrics.getOrCreate(mergeLatencyConfig);
        flushBacklogSize = metrics.getOrCreate(flushBacklogSizeConfig);
        flushCounter = metrics.getOrCreate(flushCounterConfig);
    }

    /**
     * Update the size statistic for the virtual map.
     *
     * @param size
     * 		the current size
     */
    public void setSize(final long size) {
        if (this.size != null) {
            this.size.set(size);
        }
    }

    /**
     * Record the virtual map is flushed, and flush latency is as specified.
     *
     * @param flushLatency
     * 		The flush latency
     */
    public void recordFlush(final double flushLatency) {
        if (this.flushCounter != null) {
            this.flushCounter.increment();
        }
        if (this.flushLatency != null) {
            this.flushLatency.update(flushLatency);
        }
    }

    /**
     * Record the current merge latency for the virtual map.
     *
     * @param mergeLatency
     * 		the current merge latency
     */
    public void recordMergeLatency(final double mergeLatency) {
        if (this.mergeLatency != null) {
            this.mergeLatency.update(mergeLatency);
        }
    }

    /**
     * Record the current number of virtual maps that are waiting to be flushed.
     *
     * @param flushBacklogSize
     * 		the number of maps that need to be flushed but have not yet been flushed
     */
    public void recordFlushBacklogSize(final int flushBacklogSize) {
        if (this.flushBacklogSize != null) {
            this.flushBacklogSize.set(flushBacklogSize);
        }
    }
}
