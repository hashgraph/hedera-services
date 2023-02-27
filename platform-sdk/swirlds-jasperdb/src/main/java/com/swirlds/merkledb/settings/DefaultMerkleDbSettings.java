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

package com.swirlds.merkledb.settings;

import static com.swirlds.common.utility.Units.GIBIBYTES_TO_BYTES;
import static com.swirlds.merkledb.collections.LongListOffHeap.DEFAULT_RESERVED_BUFFER_LENGTH;

import com.swirlds.merkledb.collections.ThreeLongsList;
import java.time.temporal.ChronoUnit;

/**
 * {@link MerkleDbSettings} implementation with defaults appropriate for JUnit tests.
 *
 * <p>Necessary for testing {@link MerkleDbSettingsFactory} client code running in an environment
 * without Browser-configured settings.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near
 *     future. If you need to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public class DefaultMerkleDbSettings implements MerkleDbSettings {

    /**
     * A default of 500 million should be big enough to allow us a few billion before having to
     * think about it
     */
    public static final int DEFAULT_MAX_NUM_OF_KEYS = 500_000_000;
    /** Default to 100% on disk */
    public static final int DEFAULT_INTERNAL_HASHES_RAM_TO_DISK_THRESHOLD = 0;

    public static final int DEFAULT_MAX_GB_RAM_FOR_MERGING = 10;
    public static final int DEFAULT_SMALL_MERGE_CUTOFF_MB = 3 * 1024;
    public static final int DEFAULT_MEDIUM_MERGE_CUTOFF_MB = 10 * 1024;
    public static final int DEFAULT_ITERATOR_INPUT_BUFFER_BYTES = 1024 * 1024;
    public static final int DEFAULT_WRITER_OUTPUT_BUFFER_BYTES = 4 * 1024 * 1024;
    public static final int DEFAULT_MOVE_LIST_CHUNK_SIZE = 500_000;
    public static final int DEFAULT_MAX_NUMBER_OF_FILES_IN_MERGE = 64;
    public static final int DEFAULT_MIN_NUMBER_OF_FILES_IN_MERGE = 8;
    public static final long DEFAULT_MERGE_ACTIVATED_PERIOD = 1L; // 1 seconds
    public static final long DEFAULT_MEDIUM_MERGE_PERIOD = 60L; // 1h
    public static final long DEFAULT_FULL_MERGE_PERIOD = 1440L; // 24h in min
    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 64L * 1024 * 1024 * 1024;
    public static final boolean DEFAULT_RECONNECT_KEY_LEAK_MITIGATION_ENABLED = false;
    public static final boolean DEFAULT_INDEX_REBUILDING_ENFORCED = false;
    public static final int DEFAULT_LEAF_RECORD_CACHE_SIZE = 1 << 20;

    // These default parameters result in a bloom filter false positive rate of less than 1/1000
    // when 1 billion
    // leaf nodes are transmitted during a reconnect.
    // https://hur.st/bloomfilter/?n=1000000000&p=1.0E-3&m=&k=
    public static final int DEFAULT_KEY_SET_BLOOM_FILTER_HASH_COUNT = 10;
    public static final long DEFAULT_KEY_SET_BLOOM_FILTER_SIZE_IN_BYTES = 2L * GIBIBYTES_TO_BYTES;
    public static final long DEFAULT_KEY_SET_HALF_DISK_HASH_MAP_SIZE = 1_000_000_000;
    public static final int DEFAULT_KEY_SET_HALF_DISK_HASH_MAP_BUFFER = 1_000_000;

    /** {@inheritDoc} */
    @Override
    public long getMaxNumOfKeys() {
        return DEFAULT_MAX_NUM_OF_KEYS;
    }

    /** {@inheritDoc} */
    @Override
    public long getInternalHashesRamToDiskThreshold() {
        return DEFAULT_INTERNAL_HASHES_RAM_TO_DISK_THRESHOLD;
    }

    /** {@inheritDoc} */
    @Override
    public int getMediumMergeCutoffMb() {
        return DEFAULT_MEDIUM_MERGE_CUTOFF_MB;
    }

    /** {@inheritDoc} */
    @Override
    public int getSmallMergeCutoffMb() {
        return DEFAULT_SMALL_MERGE_CUTOFF_MB;
    }

    /** {@inheritDoc} */
    @Override
    public ChronoUnit getMergePeriodUnit() {
        return ChronoUnit.MINUTES;
    }

    /** {@inheritDoc} */
    @Override
    public int getMaxNumberOfFilesInMerge() {
        return DEFAULT_MAX_NUMBER_OF_FILES_IN_MERGE;
    }

    /** {@inheritDoc} */
    @Override
    public int getMinNumberOfFilesInMerge() {
        return DEFAULT_MIN_NUMBER_OF_FILES_IN_MERGE;
    }

    /** {@inheritDoc} */
    @Override
    public long getMergeActivatePeriod() {
        return DEFAULT_MERGE_ACTIVATED_PERIOD;
    }

    /** {@inheritDoc} */
    @Override
    public long getMediumMergePeriod() {
        return DEFAULT_MEDIUM_MERGE_PERIOD;
    }

    /** {@inheritDoc} */
    @Override
    public long getFullMergePeriod() {
        return DEFAULT_FULL_MERGE_PERIOD;
    }

    /** {@inheritDoc} */
    @Override
    public long getMaxDataFileBytes() {
        return DEFAULT_MAX_FILE_SIZE_BYTES;
    }

    /** {@inheritDoc} */
    @Override
    public int getMoveListChunkSize() {
        return ThreeLongsList.SMALL_TRIPLES_PER_CHUNK;
    }

    /** {@inheritDoc} */
    @Override
    public int getMaxRamUsedForMergingGb() {
        return DEFAULT_MAX_GB_RAM_FOR_MERGING;
    }

    /** {@inheritDoc} */
    @Override
    public int getIteratorInputBufferBytes() {
        return DEFAULT_ITERATOR_INPUT_BUFFER_BYTES;
    }

    /** {@inheritDoc} */
    @Override
    public int getWriterOutputBufferBytes() {
        return DEFAULT_WRITER_OUTPUT_BUFFER_BYTES;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isReconnectKeyLeakMitigationEnabled() {
        return DEFAULT_RECONNECT_KEY_LEAK_MITIGATION_ENABLED;
    }

    /** {@inheritDoc} */
    @Override
    public int getKeySetBloomFilterHashCount() {
        return DEFAULT_KEY_SET_BLOOM_FILTER_HASH_COUNT;
    }

    /** {@inheritDoc} */
    @Override
    public long getKeySetBloomFilterSizeInBytes() {
        return DEFAULT_KEY_SET_BLOOM_FILTER_SIZE_IN_BYTES;
    }

    /** {@inheritDoc} */
    @Override
    public long getKeySetHalfDiskHashMapSize() {
        return DEFAULT_KEY_SET_HALF_DISK_HASH_MAP_SIZE;
    }

    /** {@inheritDoc} */
    @Override
    public int getKeySetHalfDiskHashMapBuffer() {
        return DEFAULT_KEY_SET_HALF_DISK_HASH_MAP_BUFFER;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isIndexRebuildingEnforced() {
        return DEFAULT_INDEX_REBUILDING_ENFORCED;
    }

    /** {@inheritDoc} */
    @Override
    public int getLeafRecordCacheSize() {
        return DEFAULT_LEAF_RECORD_CACHE_SIZE;
    }

    @Override
    public int getReservedBufferLengthForLeafList() {
        return DEFAULT_RESERVED_BUFFER_LENGTH;
    }
}
