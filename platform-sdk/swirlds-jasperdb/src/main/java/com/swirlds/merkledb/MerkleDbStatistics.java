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

package com.swirlds.merkledb;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;

import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.LongGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.common.utility.CommonUtils;

/**
 * Encapsulates statistics for an instance of a {@link MerkleDbDataSource}.
 */
public class MerkleDbStatistics {

    public static final String STAT_CATEGORY = "merkle-db";

    /** Prefix for all data source related metrics */
    private static final String DS_PREFIX = "ds_";
    /** Prefix for all files related metrics */
    private static final String FILES_PREFIX = "files_";
    /** Prefix for all metrics related to store queries */
    private static final String QUERIES_PREFIX = "queries_";
    /** Prefix for all metrics related to data flushing */
    private static final String FLUSHES_PREFIX = "flushes_";
    /** Prefix for compaction related metrics */
    private static final String COMPACTIONS_PREFIX = "compactions_";
    /** Prefix for all off-heap related metrics */
    private static final String OFFHEAP_PREFIX = "offheap_";

    private final String label;

    /** Hashes - reads / s */
    private CountPerSecond hashReadsPerSec;
    /** Hashes - writes / s */
    private CountPerSecond hashWritesPerSec;
    /** Leaves - reads / s */
    private CountPerSecond leafReadsPerSec;
    /** Leaves - writes / s */
    private CountPerSecond leafWritesPerSec;
    /** Leaf keys - reads / s */
    private CountPerSecond leafKeyReadsPerSec;
    /** Leaf keys - writes / s */
    private CountPerSecond leafKeyWritesPerSec;

    /** Hashes store - file count */
    private IntegerGauge hashesStoreFileCount;
    /** Hashes store - total file size in Mb */
    private IntegerGauge hashesStoreFileSizeMb;
    /** Leaves store - file count */
    private IntegerGauge leavesStoreFileCount;
    /** Leaves store - total file size in Mb */
    private IntegerGauge leavesStoreFileSizeMb;
    /** Leaf keys store - file count */
    private IntegerGauge leafKeysStoreFileCount;
    /** Leaf keys store - total file size in Mb */
    private IntegerGauge leafKeysStoreFileSizeMb;
    /** Total file size in Mb */
    // Should all file sizes be doubles?
    private IntegerGauge totalFileSizeMb;

    private LongGauge flushHashesWritten;
    private DoubleGauge flushHashesStoreFileSizeMb;
    private LongGauge flushLeavesWritten;
    private LongGauge flushLeavesDeleted;
    private DoubleGauge flushLeavesStoreFileSizeMb;
    private DoubleGauge flushLeafKeysStoreFileSizeMb;

    /** Hashes store small compactions - time in ms */
    private LongGauge hashesStoreSmallCompactionTimeMs;
    /** Hashes store small compactions - saved space in Mb */
    private DoubleGauge hashesStoreSmallCompactionSavedSpaceMb;
    /** Hashes store medium compactions - time in ms */
    private LongGauge hashesStoreMediumCompactionTimeMs;
    /** Hashes store medium compactions - saved space in Mb */
    private DoubleGauge hashesStoreMediumCompactionSavedSpaceMb;
    /** Hashes store full compactions - time in ms */
    private LongGauge hashesStoreFullCompactionTimeMs;
    /** Hashes store full compactions - saved space in Mb */
    private DoubleGauge hashesStoreFullCompactionSavedSpaceMb;
    /** Leaves store small compactions - time in ms */
    private LongGauge leavesStoreSmallCompactionTimeMs;
    /** Leaves store small compactions - saved space in Mb */
    private DoubleGauge leavesStoreSmallCompactionSavedSpaceMb;
    /** Leaves store medium compactions - time in ms */
    private LongGauge leavesStoreMediumCompactionTimeMs;
    /** Leaves store medium compactions - saved space in Mb */
    private DoubleGauge leavesStoreMediumCompactionSavedSpaceMb;
    /** Leaves store full compactions - time in ms */
    private LongGauge leavesStoreFullCompactionTimeMs;
    /** Leaves store full compactions - saved space in Mb */
    private DoubleGauge leavesStoreFullCompactionSavedSpaceMb;
    /** Leaf keys store small compactions - time in ms */
    private LongGauge leafKeysStoreSmallCompactionTimeMs;
    /** Leaf keys store small compactions - saved space in Mb */
    private DoubleGauge leafKeysStoreSmallCompactionSavedSpaceMb;
    /** Leaf keys store medium compactions - time in ms */
    private LongGauge leafKeysStoreMediumCompactionTimeMs;
    /** Leaf keys store medium compactions - saved space in Mb */
    private DoubleGauge leafKeysStoreMediumCompactionSavedSpaceMb;
    /** Leaf keys store full compactions - time in ms */
    private LongGauge leafKeysStoreFullCompactionTimeMs;
    /** Leaf keys store full compactions - saved space in Mb */
    private DoubleGauge leafKeysStoreFullCompactionSavedSpaceMb;

    /** Off-heap usage in MB of hashes store index */
    private IntegerGauge offHeapHashesIndexMb;
    /** Off-heap usage in MB of leaves store index */
    private IntegerGauge offHeapLeavesIndexMb;
    /** Off-heap usage in MB of leaf keys store index */
    private IntegerGauge offHeapLongKeysIndexMb;
    /** Off-heap usage in MB of object keys store bucket index */
    private IntegerGauge offHeapObjectKeyBucketsIndexMb;
    /** Off-heap usage in MB of hashes list in RAM */
    private IntegerGauge offHeapHashesListMb;
    /** Total data source off-heap usage in MB */
    private IntegerGauge offHeapDataSourceMb;

    /**
     * Create a new statistics object for a MerkleDb instances.
     *
     * @param label         the label for the virtual map
     * @throws IllegalArgumentException if {@code label} is {@code null}
     */
    public MerkleDbStatistics(final String label) {
        this.label = CommonUtils.throwArgNull(label, "label");
    }

    private static CountPerSecond buildCountPerSecond(
            final Metrics metrics, final String name, final String description) {
        return new CountPerSecond(metrics, new CountPerSecond.Config(STAT_CATEGORY, name).withDescription(description));
    }

    private static DoubleGauge buildDoubleGauge(final Metrics metrics, final String name, final String description) {
        return metrics.getOrCreate(new DoubleGauge.Config(STAT_CATEGORY, name)
                .withDescription(description)
                .withFormat(FORMAT_9_6));
    }

    private static IntegerGauge buildIntegerGauge(final Metrics metrics, final String name, final String description) {
        return metrics.getOrCreate(new IntegerGauge.Config(STAT_CATEGORY, name).withDescription(description));
    }

    private static LongGauge buildLongGauge(final Metrics metrics, final String name, final String description) {
        return metrics.getOrCreate(new LongGauge.Config(STAT_CATEGORY, name).withDescription(description));
    }

    /**
     * Register all statistics with a registry.
     *
     * @param metrics
     * 		reference to the metrics system
     * @throws IllegalArgumentException if {@code metrics} is {@code null}
     */
    public void registerMetrics(final Metrics metrics) {
        CommonUtils.throwArgNull(metrics, "metrics");

        // Queries per second
        hashWritesPerSec = buildCountPerSecond(
                metrics,
                DS_PREFIX + QUERIES_PREFIX + "hashWrites/s_" + label,
                "Number of hash writes, " + label + ", per second");
        hashReadsPerSec = buildCountPerSecond(
                metrics,
                DS_PREFIX + QUERIES_PREFIX + "hashReads/s_" + label,
                "Number of hash reads, " + label + ", per second");
        leafWritesPerSec = buildCountPerSecond(
                metrics,
                DS_PREFIX + QUERIES_PREFIX + "leafWrites/s_" + label,
                "Number of leaf writes, " + label + ", per second");
        leafReadsPerSec = buildCountPerSecond(
                metrics,
                DS_PREFIX + QUERIES_PREFIX + "leafReads/s_" + label,
                "Number of leaf reads, " + label + ", per second");
        leafKeyWritesPerSec = buildCountPerSecond(
                metrics,
                DS_PREFIX + QUERIES_PREFIX + "leafKeyWrites/s_" + label,
                "Number of leaf key writes, " + label + ", per second");
        leafKeyReadsPerSec = buildCountPerSecond(
                metrics,
                DS_PREFIX + QUERIES_PREFIX + "leafKeyReads/s_" + label,
                "Number of leaf key reads, " + label + ", per second");

        // File counts and sizes
        hashesStoreFileCount = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, DS_PREFIX + FILES_PREFIX + "hashesStoreFileCount_" + label)
                        .withDescription("File count, hashes store, " + label));
        hashesStoreFileSizeMb = buildIntegerGauge(
                metrics,
                DS_PREFIX + FILES_PREFIX + "hashesStoreFileSizeMb_" + label,
                "File size, hashes store, " + label + ", Mb");
        leavesStoreFileCount = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, DS_PREFIX + FILES_PREFIX + "leavesStoreFileCount_" + label)
                        .withDescription("File count, leaf keys store, " + label));
        leavesStoreFileSizeMb = buildIntegerGauge(
                metrics,
                DS_PREFIX + FILES_PREFIX + "leavesStoreFileSizeMb_" + label,
                "File size, leaf keys store, " + label + ", Mb");
        leafKeysStoreFileCount = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, DS_PREFIX + FILES_PREFIX + "leafKeysStoreFileCount_" + label)
                        .withDescription("File count, leaves store, " + label));
        leafKeysStoreFileSizeMb = buildIntegerGauge(
                metrics,
                DS_PREFIX + FILES_PREFIX + "leafKeysStoreFileSizeMb_" + label,
                "File size, leaves store, " + label + ", Mb");
        totalFileSizeMb = buildIntegerGauge(
                metrics,
                DS_PREFIX + FILES_PREFIX + "totalSizeMb_" + label,
                "Total file size, data source, " + label + ", Mb");

        // Flushes
        flushHashesWritten = buildLongGauge(
                metrics,
                DS_PREFIX + FLUSHES_PREFIX + "hashesWritten_" + label,
                "Number of hashes written during flush, " + label);
        flushHashesStoreFileSizeMb = buildDoubleGauge(
                metrics,
                DS_PREFIX + FLUSHES_PREFIX + "hashesStoreFileSizeMb_" + label,
                "Size of the new hashes store file created during flush, " + label + ", Mb");
        flushLeavesWritten = buildLongGauge(
                metrics,
                DS_PREFIX + FLUSHES_PREFIX + "leavesWritten_" + label,
                "Number of leaves written during flush, " + label);
        flushLeavesDeleted = buildLongGauge(
                metrics,
                DS_PREFIX + FLUSHES_PREFIX + "leavesDeleted_" + label,
                "Number of leaves deleted during flush, " + label);
        flushLeavesStoreFileSizeMb = buildDoubleGauge(
                metrics,
                DS_PREFIX + FLUSHES_PREFIX + "leavesStoreFileSizeMb_" + label,
                "Size of the new leaves store file created during flush, " + label + ", Mb");
        flushLeafKeysStoreFileSizeMb = buildDoubleGauge(
                metrics,
                DS_PREFIX + FLUSHES_PREFIX + "leafKeysStoreFileSizeMb_" + label,
                "Size of the new leaf keys store file created during flush, " + label + ", Mb");

        // Compaction
        // Hashes store
        hashesStoreSmallCompactionTimeMs = buildLongGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "hashesSmallTimeMs_" + label,
                "Small compactions time, hashes store, " + label + ", ms");
        hashesStoreSmallCompactionSavedSpaceMb = buildDoubleGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "hashesSmallSavedSpaceMb_" + label,
                "Saved space during small compactions, hashes store, " + label + ", Mb");
        hashesStoreMediumCompactionTimeMs = buildLongGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "hashesMediumTimeMs_" + label,
                "Medium compactions time, hashes store, " + label + ", ms");
        hashesStoreMediumCompactionSavedSpaceMb = buildDoubleGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "hashesMediumSavedSpaceMb_" + label,
                "Saved space during medium compactions, hashes store, " + label + ", Mb");
        hashesStoreFullCompactionTimeMs = buildLongGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "hashesFullTimeMs_" + label,
                "Full compactions time, hashes store, " + label + ", ms");
        hashesStoreFullCompactionSavedSpaceMb = buildDoubleGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "hashesFullSavedSpaceMb_" + label,
                "Saved space during full compactions, hashes store, " + label + ", Mb");
        // Leaves store
        leavesStoreSmallCompactionTimeMs = buildLongGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "leavesSmallTimeMs_" + label,
                "Small compactions time, leaves store, " + label + ", ms");
        leavesStoreSmallCompactionSavedSpaceMb = buildDoubleGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "leavesSmallSavedSpaceMb_" + label,
                "Saved space during small compactions, leaves store, " + label + ", Mb");
        leavesStoreMediumCompactionTimeMs = buildLongGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "leavesMediumTimeMs_" + label,
                "Medium compactions time, leaves store, " + label + ", ms");
        leavesStoreMediumCompactionSavedSpaceMb = buildDoubleGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "leavesMediumSavedSpaceMb_" + label,
                "Saved space during medium compactions, leaves store, " + label + ", Mb");
        leavesStoreFullCompactionTimeMs = buildLongGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "leavesFullTimeMs_" + label,
                "Full compactions time, leaves store, " + label + ", ms");
        leavesStoreFullCompactionSavedSpaceMb = buildDoubleGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "leavesFullSavedSpaceMb_" + label,
                "Saved space during full compactions, leaves store, " + label + ", Mb");
        // Leaf keys store
        leafKeysStoreSmallCompactionTimeMs = buildLongGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "leafKeysSmallTimeMs_" + label,
                "Small compactions time, leaf keys store, " + label + ", ms");
        leafKeysStoreSmallCompactionSavedSpaceMb = buildDoubleGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "leafKeysSmallSavedSpaceMb_" + label,
                "Saved space during small compactions, leaf keys store, " + label + ", Mb");
        leafKeysStoreMediumCompactionTimeMs = buildLongGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "leafKeysMediumTimeMs_" + label,
                "Medium compactions time, leaf keys store, " + label + ", ms");
        leafKeysStoreMediumCompactionSavedSpaceMb = buildDoubleGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "leafKeysMediumSavedSpaceMb_" + label,
                "Saved space during medium compactions, leaf keys store, " + label + ", Mb");
        leafKeysStoreFullCompactionTimeMs = buildLongGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "leafKeysFullTimeMs_" + label,
                "Full compactions time, leaf keys store, " + label + ", ms");
        leafKeysStoreFullCompactionSavedSpaceMb = buildDoubleGauge(
                metrics,
                DS_PREFIX + COMPACTIONS_PREFIX + "leafKeysFullSavedSpaceMb_" + label,
                "Saved space during full compactions, leaf keys store, " + label + ", Mb");

        // Off-heap usage
        offHeapHashesIndexMb = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, DS_PREFIX + OFFHEAP_PREFIX + "hashesIndexMb_" + label)
                        .withDescription("Off-heap usage, hashes store index, " + label + ", Mb"));
        offHeapLeavesIndexMb = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, DS_PREFIX + OFFHEAP_PREFIX + "leavesIndexMb_" + label)
                        .withDescription("Off-heap usage, leaves store index, " + label + ", Mb"));
        offHeapLongKeysIndexMb = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, DS_PREFIX + OFFHEAP_PREFIX + "longKeysIndexMb_" + label)
                        .withDescription("Off-heap usage, long leaf keys store index, " + label + ", Mb"));
        offHeapObjectKeyBucketsIndexMb = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, DS_PREFIX + OFFHEAP_PREFIX + "objectKeyBucketsIndexMb_" + label)
                        .withDescription("Off-heap usage, object leaf key buckets store index, " + label + ", Mb"));
        offHeapHashesListMb = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, DS_PREFIX + OFFHEAP_PREFIX + "hashesListMb_" + label)
                        .withDescription("Off-heap usage, hashes list, " + label + ", Mb"));
        offHeapDataSourceMb = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, DS_PREFIX + OFFHEAP_PREFIX + "dataSourceMb_" + label)
                        .withDescription("Off-heap usage, data source, " + label + ", Mb"));
    }

    /**
     * Increments {@link #hashWritesPerSec} stat by 1
     */
    public void countHashWrites() {
        if (hashWritesPerSec != null) {
            hashWritesPerSec.count();
        }
    }

    /**
     * Increments {@link #hashReadsPerSec} stat by 1
     */
    public void countHashReads() {
        if (hashReadsPerSec != null) {
            hashReadsPerSec.count();
        }
    }

    /**
     * Increments {@link #leafWritesPerSec} stat by 1
     */
    public void countLeafWrites() {
        if (leafWritesPerSec != null) {
            leafWritesPerSec.count();
        }
    }

    /**
     * Increments {@link #leafReadsPerSec} stat by 1
     */
    public void countLeafReads() {
        if (leafReadsPerSec != null) {
            leafReadsPerSec.count();
        }
    }

    /**
     * Increments {@link #leafKeyWritesPerSec} stat by 1
     */
    public void countLeafKeyWrites() {
        if (leafKeyWritesPerSec != null) {
            leafKeyWritesPerSec.count();
        }
    }

    /**
     * Increment {@link #leafKeyReadsPerSec} stat by 1
     */
    public void countLeafKeyReads() {
        if (leafKeyReadsPerSec != null) {
            leafKeyReadsPerSec.count();
        }
    }

    /**
     * Set the current value for the {@link #hashesStoreFileCount} stat
     *
     * @param value
     * 		the value to set
     */
    public void setHashesStoreFileCount(final int value) {
        if (hashesStoreFileCount != null) {
            hashesStoreFileCount.set(value);
        }
    }

    /**
     * Set the current value for the {@link #hashesStoreFileSizeMb} stat
     *
     * @param value
     * 		the value to set
     */
    public void setHashesStoreFileSizeMb(final int value) {
        if (hashesStoreFileSizeMb != null) {
            hashesStoreFileSizeMb.set(value);
        }
    }

    /**
     * Set the current value for the {@link #leafKeysStoreFileCount} stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeafKeysStoreFileCount(final int value) {
        if (leafKeysStoreFileCount != null) {
            leafKeysStoreFileCount.set(value);
        }
    }

    /**
     * Set the current value for the {@link #leafKeysStoreFileSizeMb} stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeafKeysStoreFileSizeMb(final int value) {
        if (leafKeysStoreFileSizeMb != null) {
            leafKeysStoreFileSizeMb.set(value);
        }
    }

    /**
     * Set the current value for the {@link #leavesStoreFileCount} stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeavesStoreFileCount(final int value) {
        if (leavesStoreFileCount != null) {
            leavesStoreFileCount.set(value);
        }
    }

    /**
     * Set the current value for the {@link #leavesStoreFileSizeMb} stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeavesStoreFileSizeMb(final int value) {
        if (leavesStoreFileSizeMb != null) {
            leavesStoreFileSizeMb.set(value);
        }
    }

    /**
     * Set the current value for the {@link #totalFileSizeMb} stat
     *
     * @param value
     * 		the value to set
     */
    public void setTotalFileSizeMb(final int value) {
        if (totalFileSizeMb != null) {
            totalFileSizeMb.set(value);
        }
    }

    public void setFlushHashesWritten(final long value) {
        if (flushHashesWritten != null) {
            flushHashesWritten.set(value);
        }
    }

    public void setFlushHashesStoreFileSizeMb(final double value) {
        if (flushHashesStoreFileSizeMb != null) {
            flushHashesStoreFileSizeMb.set(value);
        }
    }

    public void setFlushLeavesWritten(final long value) {
        if (flushLeavesWritten != null) {
            flushLeavesWritten.set(value);
        }
    }

    public void setFlushLeavesDeleted(final long value) {
        if (flushLeavesDeleted != null) {
            flushLeavesDeleted.set(value);
        }
    }

    public void setFlushLeavesStoreFileSizeMb(final double value) {
        if (flushLeavesStoreFileSizeMb != null) {
            flushLeavesStoreFileSizeMb.set(value);
        }
    }

    public void setFlushLeafKeysStoreFileSizeMb(final double value) {
        if (flushLeafKeysStoreFileSizeMb != null) {
            flushLeafKeysStoreFileSizeMb.set(value);
        }
    }

    /**
     * Set the current value for the {@link #hashesStoreSmallCompactionTimeMs},
     * {@link #hashesStoreMediumCompactionTimeMs}, or {@link #hashesStoreFullCompactionTimeMs}
     * metric based on the given compaction type.
     *
     * @param value the value to set
     */
    public void setHashesStoreCompactionTimeMs(final CompactionType type, final long value) {
        final LongGauge metric =
                switch (type) {
                    case SMALL -> hashesStoreSmallCompactionTimeMs;
                    case MEDIUM -> hashesStoreMediumCompactionTimeMs;
                    case FULL -> hashesStoreFullCompactionTimeMs;
                };
        if (metric != null) {
            metric.set(value);
        }
    }

    /**
     * Set the current value for the {@link #hashesStoreSmallCompactionSavedSpaceMb},
     * {@link #hashesStoreMediumCompactionSavedSpaceMb}, or {@link #hashesStoreFullCompactionSavedSpaceMb}
     * metric based on the given compaction type.
     *
     * @param value the value to set
     */
    public void setHashesStoreCompactionSavedSpaceMb(final CompactionType type, final double value) {
        final DoubleGauge metric =
                switch (type) {
                    case SMALL -> hashesStoreSmallCompactionSavedSpaceMb;
                    case MEDIUM -> hashesStoreMediumCompactionSavedSpaceMb;
                    case FULL -> hashesStoreFullCompactionSavedSpaceMb;
                };
        if (metric != null) {
            metric.set(value);
        }
    }

    /**
     * Set the current value for the {@link #leavesStoreSmallCompactionTimeMs},
     * {@link #leavesStoreMediumCompactionTimeMs}, or {@link #leavesStoreFullCompactionTimeMs}
     * metric based on the given compaction type.
     *
     * @param value the value to set
     */
    public void setLeavesStoreCompactionTimeMs(final CompactionType type, final long value) {
        final LongGauge metric =
                switch (type) {
                    case SMALL -> leavesStoreSmallCompactionTimeMs;
                    case MEDIUM -> leavesStoreMediumCompactionTimeMs;
                    case FULL -> leavesStoreFullCompactionTimeMs;
                };
        if (metric != null) {
            metric.set(value);
        }
    }

    /**
     * Set the current value for the {@link #leavesStoreSmallCompactionSavedSpaceMb},
     * {@link #leavesStoreMediumCompactionSavedSpaceMb}, or {@link #leavesStoreFullCompactionSavedSpaceMb}
     * metric based on the given compaction type.
     *
     * @param value the value to set
     */
    public void setLeavesStoreCompactionSavedSpaceMb(final CompactionType type, final double value) {
        final DoubleGauge metric =
                switch (type) {
                    case SMALL -> leavesStoreSmallCompactionSavedSpaceMb;
                    case MEDIUM -> leavesStoreMediumCompactionSavedSpaceMb;
                    case FULL -> leavesStoreFullCompactionSavedSpaceMb;
                };
        if (metric != null) {
            metric.set(value);
        }
    }

    /**
     * Set the current value for the {@link #leafKeysStoreSmallCompactionTimeMs},
     * {@link #leafKeysStoreMediumCompactionTimeMs}, or {@link #leafKeysStoreFullCompactionTimeMs}
     * metric based on the given compaction type.
     *
     * @param value the value to set
     */
    public void setLeafKeysStoreCompactionTimeMs(final CompactionType type, final long value) {
        final LongGauge metric =
                switch (type) {
                    case SMALL -> leafKeysStoreSmallCompactionTimeMs;
                    case MEDIUM -> leafKeysStoreMediumCompactionTimeMs;
                    case FULL -> leafKeysStoreFullCompactionTimeMs;
                };
        if (metric != null) {
            metric.set(value);
        }
    }

    /**
     * Set the current value for the {@link #leafKeysStoreSmallCompactionSavedSpaceMb},
     * {@link #leafKeysStoreMediumCompactionSavedSpaceMb}, or {@link #leafKeysStoreFullCompactionSavedSpaceMb}
     * metric based on the given compaction type.
     *
     * @param value the value to set
     */
    public void setLeafKeysStoreCompactionSavedSpaceMb(final CompactionType type, final double value) {
        final DoubleGauge metric =
                switch (type) {
                    case SMALL -> leafKeysStoreSmallCompactionSavedSpaceMb;
                    case MEDIUM -> leafKeysStoreMediumCompactionSavedSpaceMb;
                    case FULL -> leafKeysStoreFullCompactionSavedSpaceMb;
                };
        if (metric != null) {
            metric.set(value);
        }
    }

    /**
     * Set the current value for the {@link #offHeapLeavesIndexMb} stat
     *
     * @param value the value to set
     */
    public void setOffHeapLeavesIndexMb(final int value) {
        if (offHeapLeavesIndexMb != null) {
            offHeapLeavesIndexMb.set(value);
        }
    }

    /**
     * Set the current value for the {@link #offHeapHashesIndexMb} stat
     *
     * @param value the value to set
     */
    public void setOffHeapHashesIndexMb(final int value) {
        if (offHeapHashesIndexMb != null) {
            offHeapHashesIndexMb.set(value);
        }
    }

    /**
     * Set the current value for the {@link #offHeapLongKeysIndexMb} stat
     *
     * @param value the value to set
     */
    public void setOffHeapLongKeysIndexMb(final int value) {
        if (offHeapLongKeysIndexMb != null) {
            offHeapLongKeysIndexMb.set(value);
        }
    }

    /**
     * Set the current value for the {@link #offHeapObjectKeyBucketsIndexMb} stat
     *
     * @param value the value to set
     */
    public void setOffHeapObjectKeyBucketsIndexMb(final int value) {
        if (offHeapObjectKeyBucketsIndexMb != null) {
            offHeapObjectKeyBucketsIndexMb.set(value);
        }
    }

    /**
     * Set the current value for {@link #offHeapHashesListMb} stat
     *
     * @param value the value to set
     */
    public void setOffHeapHashesListMb(final int value) {
        if (offHeapHashesListMb != null) {
            offHeapHashesListMb.set(value);
        }
    }

    /**
     * Set the current value for the {@link #offHeapDataSourceMb} stat
     *
     * @param value the value to set
     */
    public void setOffHeapDataSourceMb(final int value) {
        if (offHeapDataSourceMb != null) {
            offHeapDataSourceMb.set(value);
        }
    }
}
