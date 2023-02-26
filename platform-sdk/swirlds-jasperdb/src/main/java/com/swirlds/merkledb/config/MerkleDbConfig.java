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

package com.swirlds.merkledb.config;

import com.swirlds.common.config.validators.DefaultConfigViolation;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.annotation.ConstraintMethod;
import com.swirlds.config.api.validation.annotation.Min;
import com.swirlds.config.api.validation.annotation.Positive;
import java.time.temporal.ChronoUnit;

/**
 * Instance-wide config for {@code MerkleDbDataSource}.
 *
 * @param maxNumOfKeys
 * 		Get the maximum number of unique keys we expect to be stored in this database. This is used for
 * 		calculating in memory index sizes. IMPORTANT: This can only be set before a new database is created, changing
 * 		on an existing database will break it.
 * @param internalHashesRamToDiskThreshold
 * 		Get threshold where we switch from storing internal hashes in ram to
 * 		storing them on disk. If it is 0 then everything is on disk, if it is Long.MAX_VALUE then everything is in ram.
 * 		Any value in the middle is the path value at
 * 		which we swap from ram to disk. This allows a tree where the lower levels of the tree nodes hashes are in ram
 * 		and the upper larger less changing layers are on disk. IMPORTANT: This can only be set before a new database is
 * 		created, changing on an existing database will break it.
 * @param mediumMergeCutoffMb
 * 		The cutoff size in MB of files to include in a "medium" merge.
 * @param smallMergeCutoffMb
 * 		The cutoff size in MB of files to include in a "small" merge.
 * @param mergePeriodUnit
 * 		The time unit to use when interpreting merge periods. Note this requires the {@code jasperDb.mergePeriodUnit}
 * 		to be one of the constants of the {@link ChronoUnit} enum ("SECONDS", "MINUTES", or "HOURS" are the most
 * 		likely to be used). Default is MINUTES.
 * @param maxNumberOfFilesInMerge
 * 		The maximum number of files to include in a single merge. Sets a maximum value for the number of files we will
 * 		permit to be used in a single merge. The merging algorithm scales at something near O(n^2), and under some
 * 		conditions can get into a runaway state where we are never able to merge all the files. By keeping the number
 * 		fixed, we can use a fixed amount of memory no matter how many files there are, and we can keep each merge round
 * 		reasonably short.
 * @param minNumberOfFilesInMerge
 * 		The minimum number of files before we do a merge. Each time it is time for a merge we will gather all files
 * 		available for merging for a small, medium or large merge. If there are less than this number then it is
 * 		acceptable to not do a merge.
 * @param mergeActivatePeriod
 * 		The minimum elapsed time in seconds between merge thread activating to check if merge is needed.
 * @param mediumMergePeriod
 * 		The minimum elapsed time in merge period units between medium merges.
 * @param fullMergePeriod
 * 		The minimum elapsed time in merge period units between full merges.
 * @param maxDataFileBytes
 * 		Chosen max size for a data file, this is a balance as fewer bigger files are faster to read
 * 		from but large files are extensive to merge. It must be less than 1024GB (as determined by
 *        {@code DataFileCommon#MAX_ADDRESSABLE_DATA_FILE_SIZE_BYTES})
 * @param moveListChunkSize
 * 		This is the size of each sub array chunk allocated as the moves list grows. It wants to be
 * 		big enough that we don't have too many arrays but small enough to not use a crazy amount of RAM for small
 * 		virtual merkle trees and in unit tests etc. Default of 500_000 seems an ok compromise at 11.5Mb.
 * @param maxRamUsedForMergingGb
 * 		Maximum amount of RAM that can be used for the moves map during merging of files.
 * 		This is for a single merge. If we do more than 1 merge at a time then this will be multiplied by number of
 * 		active merges. This directly dictates the max number of items that can be stored in a data file.
 * @param iteratorInputBufferBytes
 * 		Size of the buffered input stream (in bytes) underlying a {@link com.swirlds.jasperdb.files.DataFileIterator}.
 * @param writerOutputBufferBytes
 * 		Size of the buffered output stream (in bytes) used by a {@link com.swirlds.jasperdb.files.DataFileWriter}.
 * @param reconnectKeyLeakMitigationEnabled
 * 		There currently exists a bug when a virtual map is reconnected that can
 * 		cause some deleted keys to leak into the datasource. If this method returns true then a mitigation strategy is
 * 		used when a leaked key is encountered, which hides the problem from the perspective of the application. This
 * 		setting exists so that we can test behavior with and without this mitigation enabled. This mitigation should
 * 		always be enabled in production environments.
 * @param keySetBloomFilterHashCount
 * 		Configuration used during a reconnect. The number of hashes used per element
 * 		inserted into a bloom filter. The number of elements that may be inserted into the bloom filter is equal to the
 * 		number of leaf nodes transmitted during the reconnect for a single virtual map. This value should be chosen so
 * 		that the bloom filter has an acceptable false positive rate when a number of elements equal to the largest
 * 		virtual map in the state are inserted into the bloom filter.
 * @param keySetBloomFilterSizeInBytes
 * 		Configuration used during a reconnect. The in-memory size of the bloom filter, in bytes. This value should be
 * 		chosen so that the bloom filter has an acceptable false positive rate when a number of elements equal to the
 * 		largest virtual map in the state are inserted into the bloom filter. This value should be chosen with the memory
 * 		available during a reconnect kept in mind. Only one such bloom filter will be in memory at any specific point in
 * 		time.
 * @param keySetHalfDiskHashMapSize
 * 		Configuration used during a reconnect. A half disk hash map is instanced during a reconnect. This parameter
 * 		configures the size of the half disk hash map. The number of elements that may be inserted into the half disk
 * 		hash map is equal to the number of leaf nodes transmitted during the reconnect for a single virtual map. This
 * 		number should be chosen so that it accommodates the largest virtual map in the state.
 * @param keySetHalfDiskHashMapBuffer
 * 		Configuration used during a reconnect. This configures the size of an in-memory buffer that is used when
 * 		writing to the half disk hash map configured by {@link #keySetHalfDiskHashMapSize()}.
 * @param indexRebuildingEnforced
 * 		Configuration used to avoid reading stored indexes from a saved state and enforce rebuilding those indexes from
 * 		data files.
 */
@ConfigData("merkleDb")
public record MerkleDbConfig(
        @Positive @ConfigProperty(defaultValue = "500000000") long maxNumOfKeys,
        @Min(0) @ConfigProperty(defaultValue = "0") long internalHashesRamToDiskThreshold,
        @ConfigProperty(defaultValue = "10240") int mediumMergeCutoffMb,
        @ConfigProperty(defaultValue = "3072") int smallMergeCutoffMb,
        @ConfigProperty(defaultValue = "MINUTES") ChronoUnit mergePeriodUnit,
        @ConstraintMethod("maxNumberOfFilesInMergeValidation") @ConfigProperty(defaultValue = "64")
                int maxNumberOfFilesInMerge,
        @ConstraintMethod("minNumberOfFilesInMergeValidation") @ConfigProperty(defaultValue = "8")
                int minNumberOfFilesInMerge,
        @Min(0) @ConfigProperty(defaultValue = "1") long mergeActivatePeriod,
        @Min(0) @ConfigProperty(defaultValue = "60") long mediumMergePeriod,
        @Min(0) @ConfigProperty(defaultValue = "1440") long fullMergePeriod,
        @Min(0) @ConfigProperty(defaultValue = "68719476736") long maxDataFileBytes,
        @Positive @ConfigProperty(defaultValue = "500000") int moveListChunkSize,
        @Min(0) @ConfigProperty(defaultValue = "10") int maxRamUsedForMergingGb,
        @Positive @ConfigProperty(defaultValue = "1048576") int iteratorInputBufferBytes,
        @Positive @ConfigProperty(defaultValue = "4194304") int writerOutputBufferBytes,
        @ConfigProperty(defaultValue = "false") boolean reconnectKeyLeakMitigationEnabled,
        @ConfigProperty(defaultValue = "10") int keySetBloomFilterHashCount,
        @ConfigProperty(defaultValue = "2147483648") long keySetBloomFilterSizeInBytes,
        @ConfigProperty(defaultValue = "1000000000") long keySetHalfDiskHashMapSize,
        @ConfigProperty(defaultValue = "1000000") int keySetHalfDiskHashMapBuffer,
        @ConfigProperty(defaultValue = "false") boolean indexRebuildingEnforced) {

    public ConfigViolation maxNumberOfFilesInMergeValidation(final Configuration configuration) {
        final long maxNumberOfFilesInMerge =
                configuration.getConfigData(MerkleDbConfig.class).maxNumberOfFilesInMerge();
        final long minNumberOfFilesInMerge =
                configuration.getConfigData(MerkleDbConfig.class).minNumberOfFilesInMerge();
        if (maxNumberOfFilesInMerge <= minNumberOfFilesInMerge) {
            return new DefaultConfigViolation(
                    "maxNumberOfFilesInMerge",
                    maxNumberOfFilesInMerge + "",
                    true,
                    "Cannot configure maxNumberOfFilesInMerge to " + maxNumberOfFilesInMerge + ", it must be > "
                            + minNumberOfFilesInMerge);
        }
        return null;
    }

    public ConfigViolation minNumberOfFilesInMergeValidation(final Configuration configuration) {
        final long maxNumberOfFilesInMerge =
                configuration.getConfigData(MerkleDbConfig.class).maxNumberOfFilesInMerge();
        final long minNumberOfFilesInMerge =
                configuration.getConfigData(MerkleDbConfig.class).minNumberOfFilesInMerge();
        if (minNumberOfFilesInMerge < 2) {
            return new DefaultConfigViolation(
                    "maxNumberOfFilesInMerge",
                    maxNumberOfFilesInMerge + "",
                    true,
                    "Cannot configure minNumberOfFilesInMerge to " + minNumberOfFilesInMerge + ", it must be >= 2");
        }
        return null;
    }
}
