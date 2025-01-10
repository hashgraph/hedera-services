/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import com.hedera.node.app.annotations.NodeSelfId;
import com.swirlds.metrics.api.LongGauge;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class BlockStreamBucketUploaderMetrics {
    private static final Logger log = LogManager.getLogger(BlockStreamBucketUploaderMetrics.class);
    public static final String PER_NODE_METRIC_PREFIX = "hedera.blocks.bucket.%s";
    public static final String PER_PROVIDER_PER_NODE_METRIC_PREFIX = "hedera.blocks.bucket.%s.%s";
    private static final String BLOCKS_RETAINED = "blocks.retained";
    private static final String BLOCKS_RETAINED_DESC =
            "Current number of blocks retained in root block file directory on disk for the node";
    private static final String BLOCKS_UPLOADED = "blocks.uploaded";
    private static final String BLOCKS_UPLOADED_DESC =
            "Current number of blocks in uploaded directory on disk for the node";
    private static final String BLOCKS_HASH_MISMATCH = "blocks.hashmismatch";
    private static final String BLOCKS_HASH_MISMATCH_DESC =
            "Current number of blocks in hashmismatch directory on disk for the node";

    private final LongGauge blocksRetained;
    private final LongGauge blocksUploaded;
    private final LongGauge blocksHashMismatch;

    /**
     * Constructor for the BlockStreamBucketMetrics.
     *
     * @param metrics the {@link Metrics} object where all metrics will be registered
     */
    @Inject
    public BlockStreamBucketUploaderMetrics(@NonNull final Metrics metrics, @NodeSelfId final long selfNodeId) {
        blocksRetained = metrics.getOrCreate(
                new LongGauge.Config(String.format(PER_NODE_METRIC_PREFIX, selfNodeId), BLOCKS_RETAINED)
                        .withDescription(BLOCKS_RETAINED_DESC));
        blocksUploaded = metrics.getOrCreate(
                new LongGauge.Config(String.format(PER_NODE_METRIC_PREFIX, selfNodeId), BLOCKS_UPLOADED)
                        .withDescription(BLOCKS_UPLOADED_DESC));
        blocksHashMismatch = metrics.getOrCreate(
                new LongGauge.Config(String.format(PER_NODE_METRIC_PREFIX, selfNodeId), BLOCKS_HASH_MISMATCH)
                        .withDescription(BLOCKS_HASH_MISMATCH_DESC));
    }

    /**
     * Update the metric for the current number of blocks retained in root block file directory.
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
     * Update the metric for the current number of blocks in uploaded directory.
     *
     * @param blocksUploadedCount current number of uploaded blocks on disk
     */
    public void updateBlocksUploadedCount(final long blocksUploadedCount) {
        if (blocksUploadedCount < 0) {
            log.warn("Received number of uploaded blocks: {}", blocksUploadedCount);
        } else {
            blocksUploaded.set(blocksUploadedCount);
        }
    }

    /**
     * Update the metric for the current number of blocks in hashmismatch directory.
     *
     * @param blocksHashMismatchCount current number of blocks with hash mismatch on disk
     */
    public void updateBlocksHashMismatchCount(final long blocksHashMismatchCount) {
        if (blocksHashMismatchCount < 0) {
            log.warn("Received number of hash mismatched blocks: {}", blocksHashMismatchCount);
        } else {
            blocksHashMismatch.set(blocksHashMismatchCount);
        }
    }
}
