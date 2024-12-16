/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks;

import com.google.common.annotations.VisibleForTesting;
import com.swirlds.metrics.api.LongGauge;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class BlockStreamBucketMetrics {
    private static final Logger log = LogManager.getLogger(BlockStreamBucketMetrics.class);
    private final LongGauge blocksRetained;
    private static final String BLOCKS_RETAINED = "blocks.retained";
    private static final String BLOCKS_RETAINED_DESC = "Current number of blocks retained on disk for the node";

    /**
     * Constructor for the BlockStreamBucketMetrics.
     *
     * @param metrics the {@link Metrics} object where all metrics will be registered
     */
    @Inject
    public BlockStreamBucketMetrics(@NonNull final Metrics metrics) {
        final var category =
                "hedera.blocks.bucket.<provider>.<nodeId>"; // TODO: hedera.blocks.bucket.<provider>.<nodeId>
        final LongGauge.Config blocksRetainedConfig =
                new LongGauge.Config(category, BLOCKS_RETAINED).withDescription(BLOCKS_RETAINED_DESC);
        blocksRetained = metrics.getOrCreate(blocksRetainedConfig);
    }

    /**
     * Current number of blocks retained on disk.
     *
     * @param blocksRetainedCount current number of blocks retained on disk
     */
    public void updateBlocksRetainedCount(final long blocksRetainedCount) {
        if (blocksRetainedCount < 0) {
            log.warn("Received number of retained blocks: {}", blocksRetainedCount);
        } else {
            blocksRetained.set(blocksRetainedCount);
        }
    }

    /**
     * @return the current number of blocks retained on disk
     */
    @VisibleForTesting
    public long getBlocksRetainedCount() {
        return blocksRetained.get();
    }
}
