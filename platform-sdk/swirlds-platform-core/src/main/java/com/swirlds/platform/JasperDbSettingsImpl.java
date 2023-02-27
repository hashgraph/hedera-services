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

package com.swirlds.platform;

import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_FULL_MERGE_PERIOD;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_INDEX_REBUILDING_ENFORCED;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_INTERNAL_HASHES_RAM_TO_DISK_THRESHOLD;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_ITERATOR_INPUT_BUFFER_BYTES;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_KEY_SET_BLOOM_FILTER_HASH_COUNT;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_KEY_SET_BLOOM_FILTER_SIZE_IN_BYTES;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_KEY_SET_HALF_DISK_HASH_MAP_BUFFER;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_KEY_SET_HALF_DISK_HASH_MAP_SIZE;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_LEAF_RECORD_CACHE_SIZE;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MAX_FILE_SIZE_BYTES;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MAX_GB_RAM_FOR_MERGING;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MAX_NUMBER_OF_FILES_IN_MERGE;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MAX_NUM_OF_KEYS;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MEDIUM_MERGE_CUTOFF_MB;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MEDIUM_MERGE_PERIOD;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MERGE_ACTIVATED_PERIOD;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MIN_NUMBER_OF_FILES_IN_MERGE;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MOVE_LIST_CHUNK_SIZE;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_RECONNECT_KEY_LEAK_MITIGATION_ENABLED;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_SMALL_MERGE_CUTOFF_MB;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_WRITER_OUTPUT_BUFFER_BYTES;

import com.swirlds.jasperdb.settings.JasperDbSettings;
import com.swirlds.platform.internal.SubSetting;
import java.time.temporal.ChronoUnit;

/**
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("unused")
public class JasperDbSettingsImpl extends SubSetting implements JasperDbSettings {
    public static final int MAX_NUMBER_OF_SAVES_BEFORE_MERGE = 100;

    public int maxNumOfKeys = DEFAULT_MAX_NUM_OF_KEYS;
    public int internalHashesRamToDiskThreshold = DEFAULT_INTERNAL_HASHES_RAM_TO_DISK_THRESHOLD;
    public int smallMergeCutoffMb = DEFAULT_SMALL_MERGE_CUTOFF_MB;
    public int mediumMergeCutoffMb = DEFAULT_MEDIUM_MERGE_CUTOFF_MB;
    public int moveListChunkSize = DEFAULT_MOVE_LIST_CHUNK_SIZE;
    public int maxRamUsedForMergingGb = DEFAULT_MAX_GB_RAM_FOR_MERGING;
    public int iteratorInputBufferBytes = DEFAULT_ITERATOR_INPUT_BUFFER_BYTES;
    public int writerOutputBufferBytes = DEFAULT_WRITER_OUTPUT_BUFFER_BYTES;
    public long maxDataFileBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
    public long fullMergePeriod = DEFAULT_FULL_MERGE_PERIOD;
    public long mediumMergePeriod = DEFAULT_MEDIUM_MERGE_PERIOD;
    public long mergeActivatedPeriod = DEFAULT_MERGE_ACTIVATED_PERIOD;
    public int maxNumberOfFilesInMerge = DEFAULT_MAX_NUMBER_OF_FILES_IN_MERGE;
    public int minNumberOfFilesInMerge = DEFAULT_MIN_NUMBER_OF_FILES_IN_MERGE;
    public boolean reconnectKeyLeakMitigationEnabled = DEFAULT_RECONNECT_KEY_LEAK_MITIGATION_ENABLED;
    public String mergePeriodUnit = "MINUTES";
    public int keySetBloomFilterHashCount = DEFAULT_KEY_SET_BLOOM_FILTER_HASH_COUNT;
    public long keySetBloomFilterSizeInBytes = DEFAULT_KEY_SET_BLOOM_FILTER_SIZE_IN_BYTES;
    public long keySetHalfDiskHashMapSize = DEFAULT_KEY_SET_HALF_DISK_HASH_MAP_SIZE;
    public int keySetHalfDiskHashMapBuffer = DEFAULT_KEY_SET_HALF_DISK_HASH_MAP_BUFFER;
    public boolean indexRebuildingEnforced = DEFAULT_INDEX_REBUILDING_ENFORCED;
    public int leafRecordCacheSize = DEFAULT_LEAF_RECORD_CACHE_SIZE;

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxNumOfKeys() {
        return maxNumOfKeys;
    }

    public void setMaxNumOfKeys(final int maxNumOfKeys) {
        if (maxNumOfKeys <= 0) {
            throw new IllegalArgumentException("Cannot configure maxNumOfKeys=" + maxNumOfKeys);
        }
        this.maxNumOfKeys = maxNumOfKeys;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getInternalHashesRamToDiskThreshold() {
        return internalHashesRamToDiskThreshold;
    }

    public void setInternalHashesRamToDiskThreshold(final int internalHashesRamToDiskThreshold) {
        if (internalHashesRamToDiskThreshold < 0) {
            throw new IllegalArgumentException(
                    "Cannot configure internalHashesRamToDiskThreshold=" + internalHashesRamToDiskThreshold);
        }
        this.internalHashesRamToDiskThreshold = internalHashesRamToDiskThreshold;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMediumMergeCutoffMb() {
        return mediumMergeCutoffMb;
    }

    public void setMediumMergeCutoffMb(final int mediumMergeCutoffMb) {
        this.mediumMergeCutoffMb = mediumMergeCutoffMb;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSmallMergeCutoffMb() {
        return smallMergeCutoffMb;
    }

    public void setSmallMergeCutoffMb(int smallMergeCutoffMb) {
        this.smallMergeCutoffMb = smallMergeCutoffMb;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChronoUnit getMergePeriodUnit() {
        return ChronoUnit.valueOf(mergePeriodUnit);
    }

    public void setMergePeriodUnit(String mergePeriodUnit) {
        ChronoUnit.valueOf(mergePeriodUnit);
        this.mergePeriodUnit = mergePeriodUnit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFullMergePeriod() {
        return fullMergePeriod;
    }

    public void setFullMergePeriod(final long fullMergePeriod) {
        if (fullMergePeriod < 0) {
            throw new IllegalArgumentException("Cannot configure fullMergePeriod=" + fullMergePeriod);
        }
        this.fullMergePeriod = fullMergePeriod;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMediumMergePeriod() {
        return mediumMergePeriod;
    }

    public void setMediumMergePeriod(final long mediumMergePeriod) {
        if (mediumMergePeriod < 0) {
            throw new IllegalArgumentException("Cannot configure mediumMergePeriod=" + mediumMergePeriod);
        }
        this.mediumMergePeriod = mediumMergePeriod;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMergeActivatePeriod() {
        return mergeActivatedPeriod;
    }

    public void setMergeActivatedPeriod(final long mergeActivatedPeriod) {
        if (mergeActivatedPeriod < 0) {
            throw new IllegalArgumentException("Cannot configure smallMergePeriod=" + mergeActivatedPeriod);
        }
        this.mergeActivatedPeriod = mergeActivatedPeriod;
    }

    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public int getMaxNumberOfFilesInMerge() {
        return maxNumberOfFilesInMerge;
    }

    public void setMaxNumberOfFilesInMerge(final int maxNumberOfFilesInMerge) {
        if (maxNumberOfFilesInMerge <= minNumberOfFilesInMerge) {
            throw new IllegalArgumentException("Cannot configure maxNumberOfFilesInMerge to " + maxNumberOfFilesInMerge
                    + ", it mist be > " + minNumberOfFilesInMerge);
        }
        this.maxNumberOfFilesInMerge = maxNumberOfFilesInMerge;
    }

    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public int getMinNumberOfFilesInMerge() {
        return minNumberOfFilesInMerge;
    }

    public void setMinNumberOfFilesInMerge(final int minNumberOfFilesInMerge) {
        if (minNumberOfFilesInMerge < 2 || minNumberOfFilesInMerge >= maxNumberOfFilesInMerge) {
            throw new IllegalArgumentException("Cannot configure minNumberOfFilesInMerge to " + minNumberOfFilesInMerge
                    + ", it must be >= 2 and < " + maxNumberOfFilesInMerge);
        }
        this.minNumberOfFilesInMerge = minNumberOfFilesInMerge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxDataFileBytes() {
        return maxDataFileBytes;
    }

    public void setMaxDataFileBytes(final long maxDataFileBytes) {
        if (maxDataFileBytes < 0) {
            throw new IllegalArgumentException("Cannot configure maxDataFileBytes=" + maxDataFileBytes);
        }
        this.maxDataFileBytes = maxDataFileBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMoveListChunkSize() {
        return moveListChunkSize;
    }

    public void setMoveListChunkSize(final int moveListChunkSize) {
        if (moveListChunkSize <= 0) {
            throw new IllegalArgumentException("Cannot configure moveListChunkSize=" + moveListChunkSize);
        }
        this.moveListChunkSize = moveListChunkSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxRamUsedForMergingGb() {
        return maxRamUsedForMergingGb;
    }

    public void setMaxRamUsedForMergingGb(final int maxRamUsedForMergingGb) {
        if (maxRamUsedForMergingGb < 0) {
            throw new IllegalArgumentException("Cannot configure maxRamUsedForMergingGb=" + maxRamUsedForMergingGb);
        }
        this.maxRamUsedForMergingGb = maxRamUsedForMergingGb;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIteratorInputBufferBytes() {
        return iteratorInputBufferBytes;
    }

    public void setIteratorInputBufferBytes(final int iteratorInputBufferBytes) {
        if (iteratorInputBufferBytes <= 0) {
            throw new IllegalArgumentException("Cannot configure iteratorInputBufferBytes=" + iteratorInputBufferBytes);
        }
        this.iteratorInputBufferBytes = iteratorInputBufferBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getWriterOutputBufferBytes() {
        return writerOutputBufferBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReconnectKeyLeakMitigationEnabled() {
        return reconnectKeyLeakMitigationEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getKeySetBloomFilterHashCount() {
        return keySetBloomFilterHashCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getKeySetBloomFilterSizeInBytes() {
        return keySetBloomFilterSizeInBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getKeySetHalfDiskHashMapSize() {
        return keySetHalfDiskHashMapSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getKeySetHalfDiskHashMapBuffer() {
        return keySetHalfDiskHashMapBuffer;
    }

    public void setWriterOutputBufferBytes(final int writerOutputBufferBytes) {
        if (writerOutputBufferBytes <= 0) {
            throw new IllegalArgumentException("Cannot configure writerOutputBufferBytes=" + writerOutputBufferBytes);
        }
        this.writerOutputBufferBytes = writerOutputBufferBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isIndexRebuildingEnforced() {
        return indexRebuildingEnforced;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLeafRecordCacheSize() {
        return leafRecordCacheSize;
    }

    public void setLeafRecordCacheSize(final int leafRecordCacheSize) {
        if (leafRecordCacheSize < 0) {
            throw new IllegalArgumentException("Cannot configure leafRecordCacheSize=" + leafRecordCacheSize);
        }
        this.leafRecordCacheSize = leafRecordCacheSize;
    }
}
