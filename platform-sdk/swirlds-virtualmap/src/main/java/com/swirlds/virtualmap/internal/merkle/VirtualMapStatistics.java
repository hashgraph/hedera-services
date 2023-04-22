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
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.common.utility.CommonUtils;

/**
 * Encapsulates statistics for a virtual map.
 */
public class VirtualMapStatistics {

    public static final String STAT_CATEGORY = "virtual-map";
    private static final double DEFAULT_HALF_LIFE = 5;

    /** Metric name prefix for all virtual map metric names */
    private static final String VMAP_PREFIX = "vmap_";
    /** Prefix for all metrics related to virtual map queries */
    private static final String QUERIES_PREFIX = "queries_";
    /** Prefix for all lifecycle related metric names */
    private static final String LIFECYCLE_PREFIX = "lifecycle_";

    /** Virtual Map name */
    private final String label;

    /** Virtual map entities - total number */
    private LongGauge size;

    /** Virtual map entities - adds / s */
    private CountPerSecond addedEntitiesPerSec;
    /** Virtual map entities - updates / s */
    private CountPerSecond updatedEntitiesPerSec;
    /** Virtual map entities - deletes / s */
    private CountPerSecond removedEntitiesPerSec;
    /** Virtual map entities - reads / s */
    private CountPerSecond readEntitiesPerSec;

    /** Number of virtual root copies in the pipeline */
    private IntegerGauge pipelineSize;
    /** The number of virtual root node copies in virtual pipeline flush backlog */
    private IntegerGauge flushBacklogSize;
    /** Flush backpressure duration, ms */
    private IntegerGauge flushBackpressureMs;
    /** The average time to merge virtual map copy to the next copy, ms */
    private RunningAverageMetric mergeDurationMs;
    /** The average time to flush virtual map copy to disk (to data source), ms */
    private RunningAverageMetric flushDurationMs;
    /** The number of virtual root node copy flushes to data source */
    private Counter flushCount;
    /** The average time to hash virtual map copy, ms */
    private RunningAverageMetric hashDurationMs;

    private static CountPerSecond buildCountPerSecond(
            final Metrics metrics, final String name, final String description) {
        return new CountPerSecond(metrics, new CountPerSecond.Config(STAT_CATEGORY, name).withDescription(description));
    }

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
        this.label = label;
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

        // Generic
        size = metrics.getOrCreate(new LongGauge.Config(STAT_CATEGORY, VMAP_PREFIX + "size_" + label)
                .withDescription("Virtual map size, " + label));

        // Queries
        addedEntitiesPerSec = buildCountPerSecond(
                metrics,
                VMAP_PREFIX + QUERIES_PREFIX + "addedEntities/s_" + label,
                "Added virtual map entities, " + label + ", per second");
        updatedEntitiesPerSec = buildCountPerSecond(
                metrics,
                VMAP_PREFIX + QUERIES_PREFIX + "updatedEntities/s_" + label,
                "Updated virtual map entities, " + label + ", per second");
        removedEntitiesPerSec = buildCountPerSecond(
                metrics,
                VMAP_PREFIX + QUERIES_PREFIX + "removedEntities/s_" + label,
                "Removed virtual map entities, " + label + ", per second");
        readEntitiesPerSec = buildCountPerSecond(
                metrics,
                VMAP_PREFIX + QUERIES_PREFIX + "readEntities/s_" + label,
                "Read virtual map entities, " + label + ", per second");

        // Lifecycle
        pipelineSize = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "pipelineSize_" + label)
                        .withDescription("Virtual pipeline size, " + label));
        flushBacklogSize = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "flushBacklogSize_" + label)
                        .withDescription("Virtual pipeline flush backlog size" + label));
        flushBackpressureMs = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "flushBackpressureMs_" + label)
                        .withDescription("Virtual pipeline flush backpressure, " + label + ", ms"));
        mergeDurationMs = metrics.getOrCreate(new RunningAverageMetric.Config(
                        STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "mergeDurationMs_" + label)
                .withFormat(FORMAT_10_2)
                .withHalfLife(DEFAULT_HALF_LIFE)
                .withDescription("Virtual root copy merge duration, " + label + ", ms"));
        flushDurationMs = metrics.getOrCreate(new RunningAverageMetric.Config(
                        STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "flushDurationMs_" + label)
                .withFormat(FORMAT_10_2)
                .withHalfLife(DEFAULT_HALF_LIFE)
                .withDescription("Virtual root copy flush duration, " + label + ", ms"));
        flushCount = metrics.getOrCreate(
                new Counter.Config(STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "flushCount_" + label)
                        .withDescription("Virtual root copy flush count, " + label));
        hashDurationMs = metrics.getOrCreate(new RunningAverageMetric.Config(
                        STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "hashDurationMs_" + label)
                .withFormat(FORMAT_10_2)
                .withHalfLife(DEFAULT_HALF_LIFE)
                .withDescription("Virtual root copy hash duration, " + label + ", ms"));
    }

    /**
     * Update the size statistic for the virtual map.
     *
     * @param size the value to set
     */
    public void setSize(final long size) {
        if (this.size != null) {
            this.size.set(size);
        }
    }

    /**
     * Increments {@link #addedEntitiesPerSec} stat by 1.
     */
    public void countAddedEntities() {
        if (addedEntitiesPerSec != null) {
            addedEntitiesPerSec.count();
        }
    }

    /**
     * Increments {@link #updatedEntitiesPerSec} stat by 1.
     */
    public void countUpdatedEntities() {
        if (updatedEntitiesPerSec != null) {
            updatedEntitiesPerSec.count();
        }
    }

    /**
     * Increments {@link #removedEntitiesPerSec} stat by 1.
     */
    public void countRemovedEntities() {
        if (removedEntitiesPerSec != null) {
            removedEntitiesPerSec.count();
        }
    }

    /**
     * Increments {@link #readEntitiesPerSec} stat by 1.
     */
    public void countReadEntities() {
        if (readEntitiesPerSec != null) {
            readEntitiesPerSec.count();
        }
    }

    public void resetEntityCounters() {
        if (addedEntitiesPerSec != null) {
            addedEntitiesPerSec.reset();
        }
        if (updatedEntitiesPerSec != null) {
            updatedEntitiesPerSec.reset();
        }
        if (removedEntitiesPerSec != null) {
            removedEntitiesPerSec.reset();
        }
        if (readEntitiesPerSec != null) {
            readEntitiesPerSec.reset();
        }
    }

    /**
     * Updates {@link #pipelineSize} stat to the given value.
     *
     * @param value the value to set
     */
    public void setPipelineSize(final int value) {
        if (this.pipelineSize != null) {
            this.pipelineSize.set(value);
        }
    }

    /**
     * Updates {@link #flushBacklogSize} stat.
     *
     * @param size flush backlog size
     */
    public void recordFlushBacklogSize(final int size) {
        if (flushBacklogSize != null) {
            flushBacklogSize.set(size);
        }
    }

    /**
     * Updates {@link #flushBackpressureMs} stat.
     *
     * @param backpressureMs flush backpressure, ms
     */
    public void recordFlushBackpressureMs(final int backpressureMs) {
        if (flushBackpressureMs != null) {
            flushBackpressureMs.set(backpressureMs);
        }
    }

    /**
     * Record a virtual root copy is merged, and merge duration is as specified.
     *
     * @param mergeDurationMs merge duration, ms
     */
    public void recordMerge(final double mergeDurationMs) {
        if (this.mergeDurationMs != null) {
            this.mergeDurationMs.update(mergeDurationMs);
        }
    }

    /**
     * Record a virtual root copy is flushed, and flush duration is as specified.
     *
     * @param flushDurationMs flush duration, ms
     */
    public void recordFlush(final double flushDurationMs) {
        if (this.flushCount != null) {
            this.flushCount.increment();
        }
        if (this.flushDurationMs != null) {
            this.flushDurationMs.update(flushDurationMs);
        }
    }

    /**
     * Record a virtual root copy is hashed, and hash duration is as specified.
     *
     * @param hashDurationMs flush duration, ms
     */
    public void recordHash(final double hashDurationMs) {
        if (this.hashDurationMs != null) {
            this.hashDurationMs.update(hashDurationMs);
        }
    }
}
