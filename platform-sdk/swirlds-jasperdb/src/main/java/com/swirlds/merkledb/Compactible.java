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

import java.io.IOException;
import java.util.LongSummaryStatistics;

/**
 * This interface indicates that the data storage files in the implementing class can be compacted
 */
public interface Compactible {
    /**
     * Compact the files in the implementing class and update the stats for total usage of disk space and off-heap space
     * if the compaction was successful
     * @return true if the compaction was successful, false otherwise
     * @throws IOException if there was a problem compaction
     * @throws InterruptedException if the compaction is interrupted
     */
    default boolean compact() throws IOException, InterruptedException {
        boolean result = doCompact();
        Runnable updateTotalStatsFunction = getUpdateTotalStatsFunction();
        if (result && updateTotalStatsFunction != null) {
            updateTotalStatsFunction.run();
        }
        return result;
    }

    /**
     * Compacts the files in the implementing class
     * @return true if the compaction was successful, false otherwise
     * @throws IOException if there was a problem compaction
     * @throws InterruptedException if the compaction is interrupted
     */
    boolean doCompact() throws IOException, InterruptedException;

    /**
     * @return the function to update the total usage stats
     */
    Runnable getUpdateTotalStatsFunction();

    /**
     * Get statistics for sizes of all files
     *
     * @return statistics for sizes of all fully written files, in bytes
     */
    LongSummaryStatistics getFilesSizeStatistics();
}
