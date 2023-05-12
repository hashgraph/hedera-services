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

package com.swirlds.jasperdb;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;

import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.utility.CommonUtils;

/**
 * Encapsulates statistics for an instance of a {@link VirtualDataSourceJasperDB}.
 */
public class JasperDbStatistics {

    public static final String STAT_CATEGORY = "jasper-db";
    private static final String NUMBER_OF_FILES_PREFIX = "The number of files ";
    private static final String TOTAL_FILES_SIZE_PREFIX = "The total files size (in megabytes) of files ";
    private static final String SMALL_MERGE_PREFIX = "The time (in seconds) of the last Small Merge call ";
    private static final String MEDIUM_MERGE_PREFIX = "The time (in seconds) of the last Medium Merge call ";
    private static final String LARGE_MERGE_PREFIX = "The time (in seconds) of the last Large Merge call ";
    private static final String INTERNAL_HASHES_STORE_MIDDLE = "in the internal Hashes Store for ";
    private static final String LEAF_KEY_TO_PATH_STORE_MIDDLE = "in the Leaf Key To Path Store for ";
    private static final String LEAF_PATH_TO_HKV_STORE_MIDDLE = "in the Leaf Path to Hash/Key/Value Store for ";
    private static final String SUFFIX = " for the last call to doMerge() or saveRecords().";
    private static final String MERGE_SUFFIX = " for the last call to doMerge().";
    private static final int SPEEDOMETER_HALF_LIFE_IN_SECONDS = 60; // look at last minute

    private final String label;
    private final boolean isLongKeyMode;

    private SpeedometerMetric internalNodeWritesPerSecond;
    private SpeedometerMetric internalNodeReadsPerSecond;
    private SpeedometerMetric leafWritesPerSecond;
    private SpeedometerMetric leafByKeyReadsPerSecond;
    private SpeedometerMetric leafByPathReadsPerSecond;

    private IntegerGauge internalHashesStoreFileCount;
    private DoubleGauge internalHashesStoreTotalFileSizeInMB;

    private IntegerGauge leafKeyToPathStoreFileCount;

    private DoubleGauge leafKeyToPathStoreTotalFileSizeInMB;

    private IntegerGauge leafPathToHashKeyValueStoreFileCount;

    private DoubleGauge leafPathToHashKeyValueStoreTotalFileSizeInMB;

    private DoubleGauge internalHashesStoreSmallMergeTime;

    private DoubleGauge internalHashesStoreMediumMergeTime;
    private DoubleGauge internalHashesStoreLargeMergeTime;
    private DoubleGauge leafKeyToPathStoreSmallMergeTime;
    private DoubleGauge leafKeyToPathStoreMediumMergeTime;
    private DoubleGauge leafKeyToPathStoreLargeMergeTime;
    private DoubleGauge leafPathToHashKeyValueStoreSmallMergeTime;
    private DoubleGauge leafPathToHashKeyValueStoreMediumMergeTime;
    private DoubleGauge leafPathToHashKeyValueStoreLargeMergeTime;

    /**
     * Create a new statistics object for a JPDB instances.
     *
     * @param label
     * 		the label for the virtual map
     * @param isLongKeyMode
     * 		true if the long key optimization is enabled
     * @throws IllegalArgumentException if {@code label} is {@code null}
     */
    public JasperDbStatistics(final String label, final boolean isLongKeyMode) {
        this.label = CommonUtils.throwArgNull(label, "label");
        this.isLongKeyMode = isLongKeyMode;
    }

    private static SpeedometerMetric buildSpeedometerMetric(
            final Metrics metrics, final String name, final String description) {

        return metrics.getOrCreate(new SpeedometerMetric.Config(STAT_CATEGORY, name)
                .withDescription(description)
                .withFormat(FORMAT_9_6)
                .withHalfLife(SPEEDOMETER_HALF_LIFE_IN_SECONDS));
    }

    private static DoubleGauge buildDoubleGauge(final Metrics metrics, final String name, final String description) {
        return metrics.getOrCreate(new DoubleGauge.Config(STAT_CATEGORY, name)
                .withDescription(description)
                .withFormat(FORMAT_9_6));
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
        internalNodeWritesPerSecond = buildSpeedometerMetric(
                metrics, "internalNodeWrites/s_" + label, "number of internal node writes per second for " + label);
        internalNodeReadsPerSecond = buildSpeedometerMetric(
                metrics, "internalNodeReads/s_" + label, "number of internal node reads per second for " + label);
        leafWritesPerSecond = buildSpeedometerMetric(
                metrics, "leafWrites/s_" + label, "number of leaf writes per second for " + label);
        leafByKeyReadsPerSecond = buildSpeedometerMetric(
                metrics, "leafByKeyReads/s_" + label, "number of leaf by key reads per second for " + label);
        leafByPathReadsPerSecond = buildSpeedometerMetric(
                metrics, "leafByPathReads/s_" + label, "number of leaf by path reads per second for " + label);
        internalHashesStoreFileCount =
                metrics.getOrCreate(new IntegerGauge.Config(STAT_CATEGORY, "internalHashFileCount_" + label)
                        .withDescription(NUMBER_OF_FILES_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + SUFFIX));
        internalHashesStoreTotalFileSizeInMB = buildDoubleGauge(
                metrics,
                "internalHashFileSizeMb_" + label,
                TOTAL_FILES_SIZE_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + SUFFIX);
        if (isLongKeyMode) {
            leafKeyToPathStoreFileCount =
                    metrics.getOrCreate(new IntegerGauge.Config(STAT_CATEGORY, "leafKeyToPathFileCount_" + label)
                            .withDescription(NUMBER_OF_FILES_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + SUFFIX));
            leafKeyToPathStoreTotalFileSizeInMB = buildDoubleGauge(
                    metrics,
                    "leafKeyToPathFileSizeMb_" + label,
                    TOTAL_FILES_SIZE_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + SUFFIX);
        }
        leafPathToHashKeyValueStoreFileCount =
                metrics.getOrCreate(new IntegerGauge.Config(STAT_CATEGORY, "leafHKVFileCount_" + label)
                        .withDescription(NUMBER_OF_FILES_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + SUFFIX));
        leafPathToHashKeyValueStoreTotalFileSizeInMB = buildDoubleGauge(
                metrics,
                "leafHKVFileSizeMb_" + label,
                TOTAL_FILES_SIZE_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + SUFFIX);
        internalHashesStoreSmallMergeTime = buildDoubleGauge(
                metrics,
                "internalHashSmallMergeTime_" + label,
                SMALL_MERGE_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + MERGE_SUFFIX);
        internalHashesStoreMediumMergeTime = buildDoubleGauge(
                metrics,
                "internalHashMediumMergeTime_" + label,
                MEDIUM_MERGE_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + MERGE_SUFFIX);
        internalHashesStoreLargeMergeTime = buildDoubleGauge(
                metrics,
                "internalHashLargeMergeTime_" + label,
                LARGE_MERGE_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + MERGE_SUFFIX);
        if (isLongKeyMode) {
            leafKeyToPathStoreSmallMergeTime = buildDoubleGauge(
                    metrics,
                    "leafKeyToPathSmallMergeTime_" + label,
                    SMALL_MERGE_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + MERGE_SUFFIX);
            leafKeyToPathStoreMediumMergeTime = buildDoubleGauge(
                    metrics,
                    "leafKeyToPathMediumMergeTime_" + label,
                    MEDIUM_MERGE_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + MERGE_SUFFIX);
            leafKeyToPathStoreLargeMergeTime = buildDoubleGauge(
                    metrics,
                    "leafKeyToPathLargeMergeTime_" + label,
                    LARGE_MERGE_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + MERGE_SUFFIX);
        }
        leafPathToHashKeyValueStoreSmallMergeTime = buildDoubleGauge(
                metrics,
                "leafHKVSmallMergeTime_" + label,
                SMALL_MERGE_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + MERGE_SUFFIX);
        leafPathToHashKeyValueStoreMediumMergeTime = buildDoubleGauge(
                metrics,
                "leafHKVMediumMergeTime_" + label,
                MEDIUM_MERGE_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + MERGE_SUFFIX);
        leafPathToHashKeyValueStoreLargeMergeTime = buildDoubleGauge(
                metrics,
                "leafHKVLargeMergeTime_" + label,
                LARGE_MERGE_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + MERGE_SUFFIX);
    }

    /**
     * Cycle the InternalNodeWritesPerSecond stat
     */
    public void cycleInternalNodeWritesPerSecond() {
        if (internalNodeWritesPerSecond != null) {
            internalNodeWritesPerSecond.cycle();
        }
    }

    /**
     * Cycle the InternalNodeReadsPerSecond stat
     */
    public void cycleInternalNodeReadsPerSecond() {
        if (internalNodeReadsPerSecond != null) {
            internalNodeReadsPerSecond.cycle();
        }
    }

    /**
     * Cycle the LeafWritesPerSecond stat
     */
    public void cycleLeafWritesPerSecond() {
        if (leafWritesPerSecond != null) {
            leafWritesPerSecond.cycle();
        }
    }

    /**
     * Cycle the LeafByKeyReadsPerSecond stat
     */
    public void cycleLeafByKeyReadsPerSecond() {
        if (leafByKeyReadsPerSecond != null) {
            leafByKeyReadsPerSecond.cycle();
        }
    }

    /**
     * Cycle the LeafByPathReadsPerSecond stat
     */
    public void cycleLeafByPathReadsPerSecond() {
        if (leafByPathReadsPerSecond != null) {
            leafByPathReadsPerSecond.cycle();
        }
    }

    /**
     * Set the current value for the InternalHashesStoreFileCount stat
     *
     * @param value
     * 		the value to set
     */
    public void setInternalHashesStoreFileCount(final int value) {
        if (internalHashesStoreFileCount != null) {
            internalHashesStoreFileCount.set(value);
        }
    }

    /**
     * Set the current value for the InternalHashesStoreTotalFileSizeInMB stat
     *
     * @param value
     * 		the value to set
     */
    public void setInternalHashesStoreTotalFileSizeInMB(final double value) {
        if (internalHashesStoreTotalFileSizeInMB != null) {
            internalHashesStoreTotalFileSizeInMB.set(value);
        }
    }

    /**
     * Set the current value for the LeafKeyToPathStoreFileCount stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeafKeyToPathStoreFileCount(final int value) {
        if (leafKeyToPathStoreFileCount != null) {
            leafKeyToPathStoreFileCount.set(value);
        }
    }

    /**
     * Set the current value for the LeafKeyToPathStoreTotalFileSizeInMB stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeafKeyToPathStoreTotalFileSizeInMB(final double value) {
        if (leafKeyToPathStoreTotalFileSizeInMB != null) {
            leafKeyToPathStoreTotalFileSizeInMB.set(value);
        }
    }

    /**
     * Set the current value for the LeafPathToHashKeyValueStoreFileCount stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeafPathToHashKeyValueStoreFileCount(final int value) {
        if (leafPathToHashKeyValueStoreFileCount != null) {
            leafPathToHashKeyValueStoreFileCount.set(value);
        }
    }

    /**
     * Set the current value for the LeafPathToHashKeyValueStoreTotalFileSizeInMB stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeafPathToHashKeyValueStoreTotalFileSizeInMB(final double value) {
        if (leafPathToHashKeyValueStoreTotalFileSizeInMB != null) {
            leafPathToHashKeyValueStoreTotalFileSizeInMB.set(value);
        }
    }

    /**
     * Set the current value for the InternalHashesStoreSmallMergeTime stat
     *
     * @param value
     * 		the value to set
     */
    public void setInternalHashesStoreSmallMergeTime(final double value) {
        if (internalHashesStoreSmallMergeTime != null) {
            internalHashesStoreSmallMergeTime.set(value);
        }
    }

    /**
     * Set the current value for the InternalHashesStoreMediumMergeTime stat
     *
     * @param value
     * 		the value to set
     */
    public void setInternalHashesStoreMediumMergeTime(final double value) {
        if (internalHashesStoreMediumMergeTime != null) {
            internalHashesStoreMediumMergeTime.set(value);
        }
    }

    /**
     * Set the current value for the InternalHashesStoreLargeMergeTime stat
     *
     * @param value
     * 		the value to set
     */
    public void setInternalHashesStoreLargeMergeTime(final double value) {
        if (internalHashesStoreLargeMergeTime != null) {
            internalHashesStoreLargeMergeTime.set(value);
        }
    }

    /**
     * Set the current value for the LeafKeyToPathStoreSmallMergeTime stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeafKeyToPathStoreSmallMergeTime(final double value) {
        if (leafKeyToPathStoreSmallMergeTime != null) {
            leafKeyToPathStoreSmallMergeTime.set(value);
        }
    }

    /**
     * Set the current value for the LeafKeyToPathStoreMediumMergeTime stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeafKeyToPathStoreMediumMergeTime(final double value) {
        if (leafKeyToPathStoreMediumMergeTime != null) {
            leafKeyToPathStoreMediumMergeTime.set(value);
        }
    }

    /**
     * Set the current value for the LeafKeyToPathStoreLargeMergeTime stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeafKeyToPathStoreLargeMergeTime(final double value) {
        if (leafKeyToPathStoreLargeMergeTime != null) {
            leafKeyToPathStoreLargeMergeTime.set(value);
        }
    }

    /**
     * Set the current value for the LeafPathToHashKeyValueStoreSmallMergeTime stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeafPathToHashKeyValueStoreSmallMergeTime(final double value) {
        if (leafPathToHashKeyValueStoreSmallMergeTime != null) {
            leafPathToHashKeyValueStoreSmallMergeTime.set(value);
        }
    }

    /**
     * Set the current value for the LeafPathToHashKeyValueStoreMediumMergeTime stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeafPathToHashKeyValueStoreMediumMergeTime(final double value) {
        if (leafPathToHashKeyValueStoreMediumMergeTime != null) {
            leafPathToHashKeyValueStoreMediumMergeTime.set(value);
        }
    }

    /**
     * Set the current value for the LeafPathToHashKeyValueStoreLargeMergeTime stat
     *
     * @param value
     * 		the value to set
     */
    public void setLeafPathToHashKeyValueStoreLargeMergeTime(final double value) {
        if (leafPathToHashKeyValueStoreLargeMergeTime != null) {
            leafPathToHashKeyValueStoreLargeMergeTime.set(value);
        }
    }
}
