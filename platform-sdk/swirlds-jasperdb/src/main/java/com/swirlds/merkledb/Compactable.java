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

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.LongSummaryStatistics;
import java.util.function.BiConsumer;

/**
 * This interface indicates that the data storage files in the implementing class can be compacted
 */
public interface Compactable {
    /**
     * Compact the files in the implementing class
     * @param reportDurationMetricFunction a function that will be called to report the duration of the compaction
     * @param reportSavedSpaceMetricFunction a function that will be called to report the amount of space saved by the compaction
     * @return true if the compaction was successful, false otherwise
     * @throws IOException if there was a problem compaction
     * @throws InterruptedException if the compaction is interrupted
     */
    boolean compact(
            @Nullable BiConsumer<Integer, Long> reportDurationMetricFunction,
            @Nullable BiConsumer<Integer, Double> reportSavedSpaceMetricFunction)
            throws IOException, InterruptedException;

    /**
     * Get statistics for sizes of all files
     *
     * @return statistics for sizes of all fully written files, in bytes
     */
    LongSummaryStatistics getFilesSizeStatistics();
}
