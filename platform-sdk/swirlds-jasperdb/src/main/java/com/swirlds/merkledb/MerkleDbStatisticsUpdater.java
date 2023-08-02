/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.units.UnitConstants.BYTES_TO_MEBIBYTES;

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.merkledb.collections.HashList;
import com.swirlds.merkledb.collections.HashListByteBuffer;
import com.swirlds.merkledb.collections.LongList;
import com.swirlds.merkledb.collections.LongListOffHeap;
import com.swirlds.merkledb.files.DataFileReader;
import com.swirlds.merkledb.files.hashmap.HalfDiskHashMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LongSummaryStatistics;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

public class MerkleDbStatisticsUpdater {

    /**
     * When we register stats for the first instance, also register the global stats. If true then
     * this is the first time stats are being registered for an instance.
     */
    private static final AtomicBoolean firstStatRegistration = new AtomicBoolean(true);

    private static final FunctionGauge.Config<Long> COUNT_OF_OPEN_DATABASES_CONFIG = new FunctionGauge.Config<>(
                    MerkleDbStatistics.STAT_CATEGORY,
                    "merkledb_count",
                    Long.class,
                    MerkleDbDataSource::getCountOfOpenDatabases)
            .withDescription("the number of MerkleDb instances that have been created but not" + " released")
            .withFormat("%d");

    private final MerkleDbStatistics statistics;
    private final MerkleDbDataSource<?, ?> dataSource;

    public MerkleDbStatisticsUpdater(@NonNull MerkleDbDataSource<?, ?> dataSource) {
        statistics = new MerkleDbStatistics(dataSource.getTableName());
        this.dataSource = dataSource;
    }

    public void registerMetrics(final Metrics metrics) {
        if (firstStatRegistration.compareAndSet(true, false)) {
            // register static/global statistics
            metrics.getOrCreate(COUNT_OF_OPEN_DATABASES_CONFIG);
        }

        // register instance statistics
        statistics.registerMetrics(metrics);
    }

    void setFlushLeafKeysStoreFileSize(final DataFileReader<?> newLeafKeysFile) {
        statistics.setFlushLeafKeysStoreFileSizeMb(
                newLeafKeysFile == null ? 0 : newLeafKeysFile.getSize() * BYTES_TO_MEBIBYTES);
    }

    void setFlushLeavesStoreFileSize(final DataFileReader<?> newLeafKeysFile) {
        statistics.setFlushLeavesStoreFileSizeMb(
                newLeafKeysFile == null ? 0 : newLeafKeysFile.getSize() * BYTES_TO_MEBIBYTES);
    }

    void setFlushHashesStoreFileSize(final DataFileReader<?> newHashesFile) {
        statistics.setFlushHashesStoreFileSizeMb(
                newHashesFile == null ? 0 : newHashesFile.getSize() * BYTES_TO_MEBIBYTES);
    }

    /**
     * Updates hashes store file stats: file count and total size in Mb. No-op if all hashes
     * are cached in RAM.
     *
     * @return hashes store file size, Mb
     */
    int updateHashesStoreFileStats() {
        if (dataSource.getHashStoreDisk() != null) {
            final LongSummaryStatistics internalHashesFileSizeStats =
                    dataSource.getHashStoreDisk().getFilesSizeStatistics();
            statistics.setHashesStoreFileCount((int) internalHashesFileSizeStats.getCount());
            final int fileSizeInMb = (int) (internalHashesFileSizeStats.getSum() * BYTES_TO_MEBIBYTES);
            statistics.setHashesStoreFileSizeMb(fileSizeInMb);
            return fileSizeInMb;
        }
        return 0;
    }

    /**
     * Updates leaves store file stats: file count and total size in Mb.
     *
     * @return leaves store file size, Mb
     */
    private int updateLeavesStoreFileStats() {
        final LongSummaryStatistics leafDataFileSizeStats =
                dataSource.getPathToKeyValue().getFilesSizeStatistics();
        statistics.setLeavesStoreFileCount((int) leafDataFileSizeStats.getCount());
        final int fileSizeInMb = (int) (leafDataFileSizeStats.getSum() * BYTES_TO_MEBIBYTES);
        statistics.setLeavesStoreFileSizeMb(fileSizeInMb);
        return fileSizeInMb;
    }

    /**
     * Updates leaf keys store file stats: file count and total size in Mb. No-op if keys are
     * longs and stored in a LongList rather than in a store on disk.
     *
     * @return leaf keys store file size, Mb
     */
    private int updateLeafKeysStoreFileStats() {
        if (dataSource.getObjectKeyToPath() != null) {
            final LongSummaryStatistics leafKeyFileSizeStats =
                    dataSource.getObjectKeyToPath().getFilesSizeStatistics();
            statistics.setLeafKeysStoreFileCount((int) leafKeyFileSizeStats.getCount());
            final int fileSizeInMb = (int) (leafKeyFileSizeStats.getSum() * BYTES_TO_MEBIBYTES);
            statistics.setLeafKeysStoreFileSizeMb(fileSizeInMb);
            return fileSizeInMb;
        }
        return 0;
    }

    void updateStoreFileStats() {
        statistics.setTotalFileSizeMb(
                updateHashesStoreFileStats() + updateLeavesStoreFileStats() + updateLeafKeysStoreFileStats());
    }

    void updateOffHeapStats() {
        int totalOffHeapMemoryConsumption = updateOffHeapStat(
                        dataSource.getPathToDiskLocationInternalNodes(), statistics::setOffHeapHashesIndexMb)
                + updateOffHeapStat(dataSource.getPathToDiskLocationLeafNodes(), statistics::setOffHeapLeavesIndexMb)
                + updateOffHeapStat(dataSource.getLongKeyToPath(), statistics::setOffHeapLongKeysIndexMb);
        if (dataSource.getObjectKeyToPath() != null) {
            totalOffHeapMemoryConsumption +=
                    updateOffHeapStat(dataSource.getObjectKeyToPath(), statistics::setOffHeapObjectKeyBucketsIndexMb);
        }
        if (dataSource.getHashStoreRam() != null) {
            totalOffHeapMemoryConsumption +=
                    updateOffHeapStat(dataSource.getHashStoreRam(), statistics::setOffHeapHashesListMb);
        }
        statistics.setOffHeapDataSourceMb(totalOffHeapMemoryConsumption);
    }

    void countLeafReads() {
        statistics.countLeafReads();
    }

    void countLeafKeyReads() {
        statistics.countLeafKeyReads();
    }

    void countHashReads() {
        statistics.countHashReads();
    }

    void countFlushLeavesWritten() {
        statistics.countFlushLeavesWritten(1);
    }

    void countFlushLeafKeysWritten() {
        statistics.countFlushLeafKeysWritten(1);
    }

    void countFlushLeavesDeleted() {
        statistics.countFlushLeavesDeleted(1);
    }

    void countFlushHashesWritten() {
        statistics.countFlushHashesWritten(1);
    }

    private static int updateOffHeapStat(final LongList longList, final IntConsumer updateFunction) {
        if (longList instanceof LongListOffHeap longListOffHeap) {
            final int result = (int) (longListOffHeap.getOffHeapConsumption() * BYTES_TO_MEBIBYTES);
            updateFunction.accept(result);
            return result;
        } else {
            return 0;
        }
    }

    private static int updateOffHeapStat(final HalfDiskHashMap<?> halfDiskHashMap, final IntConsumer updateFunction) {
        final int usage = (int) (halfDiskHashMap.getOffHeapConsumption() * BYTES_TO_MEBIBYTES);
        updateFunction.accept(usage);
        return usage;
    }

    private static int updateOffHeapStat(final HashList hashList, final IntConsumer updateFunction) {
        if (hashList instanceof HashListByteBuffer hashListOffHeap) {
            final int usage = (int) (hashListOffHeap.getOffHeapConsumption() * BYTES_TO_MEBIBYTES);
            updateFunction.accept(usage);
            return usage;
        } else {
            return 0;
        }
    }

    void setLeafKeysStoreCompactionTimeMs(Integer compactionLevel, Long time) {
        statistics.setLeafKeysStoreCompactionTimeMs(compactionLevel, time);
    }

    void setLeafKeysStoreCompactionSavedSpaceMb(Integer compactionLevel, Double savedSpace) {
        statistics.setLeafKeysStoreCompactionSavedSpaceMb(compactionLevel, savedSpace);
    }

    void setHashesStoreCompactionTimeMs(Integer compactionLevel, Long time) {
        statistics.setHashesStoreCompactionTimeMs(compactionLevel, time);
    }

    void setHashesStoreCompactionSavedSpaceMb(Integer compactionLevel, Double savedSpace) {
        statistics.setHashesStoreCompactionSavedSpaceMb(compactionLevel, savedSpace);
    }

    void setLeavesStoreCompactionTimeMs(Integer compactionType, Long time) {
        statistics.setLeavesStoreCompactionTimeMs(compactionType, time);
    }

    void setLeavesStoreCompactionSavedSpaceMb(Integer compactionType, Double savedSpace) {
        statistics.setLeavesStoreCompactionSavedSpaceMb(compactionType, savedSpace);
    }
}
