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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.annotations.NodeSelfId;
import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.LongGauge;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class BlockStreamBucketUploaderMetrics {
    private static final Logger log = LogManager.getLogger(BlockStreamBucketUploaderMetrics.class);
    public static final String PER_NODE_METRIC_PREFIX = "hedera.blocks.bucket.%s";
    public static final String PER_PROVIDER_PER_NODE_METRIC_PREFIX = "hedera.blocks.bucket.%s.%s";
    public static final String BLOCKS_RETAINED = "blocks.retained";
    private static final String BLOCKS_RETAINED_DESC =
            "Current number of blocks retained in root block file directory on disk for the node";
    public static final String BLOCKS_UPLOADED = "blocks.uploaded";
    private static final String BLOCKS_UPLOADED_DESC =
            "Current number of blocks in uploaded directory on disk for the node";
    public static final String BLOCKS_HASH_MISMATCH = "blocks.hashmismatch";
    private static final String BLOCKS_HASH_MISMATCH_DESC =
            "Current number of blocks in hashmismatch directory on disk for the node";
    public static final String UPLOADS_SUCCESS = "uploads.success";
    private static final String UPLOADS_SUCCESS_DESC = "Number of successful block uploads per provider per node";
    public static final String UPLOADS_FAILURE = "uploads.failure";
    private static final String UPLOADS_FAILURE_DESC = "Number of failed block uploads per provider per node";
    public static final String HASH_MISMATCH = "hash.mismatch";
    private static final String HASH_MISMATCH_DESC = "Number of hash validation failures per provider per node";
    public static final String UPLOAD_LATENCY_LATEST = "upload.latency.latest";
    public static final String UPLOAD_LATENCY_AVG = "upload.latency.avg";

    private final LongGauge blocksRetained;
    private final LongGauge blocksUploaded;
    private final LongGauge blocksHashMismatch;

    private final Map<String, Counter> successfulUploads = new HashMap<>();
    private final Map<String, Counter> failedUploads = new HashMap<>();
    private final Map<String, Counter> hashMismatches = new HashMap<>();

    private record UploadLatencyMetric(DurationGauge latest, IntegerPairAccumulator<Integer> avg) {}

    private static final BinaryOperator<Integer> AVERAGE = (sum, count) -> count == 0 ? 0 : sum / count;
    private final Map<String, UploadLatencyMetric> uploadLatencies = new HashMap<>();

    /**
     * Constructor for the BlockStreamBucketMetrics.
     *
     * @param metrics the {@link Metrics} object where all metrics will be registered
     * @param selfNodeId the ID this node
     * @param bucketProviders cloud bucket providers
     */
    @Inject
    public BlockStreamBucketUploaderMetrics(
            @NonNull final Metrics metrics,
            @NodeSelfId final long selfNodeId,
            @NonNull final List<String> bucketProviders) {
        requireNonNull(metrics, "metrics must not be null");
        requireNonNull(bucketProviders, "bucketProviders must not be null");

        final var perNodeMetricCategory = String.format(PER_NODE_METRIC_PREFIX, selfNodeId);
        blocksRetained = metrics.getOrCreate(
                new LongGauge.Config(perNodeMetricCategory, BLOCKS_RETAINED).withDescription(BLOCKS_RETAINED_DESC));
        blocksUploaded = metrics.getOrCreate(
                new LongGauge.Config(perNodeMetricCategory, BLOCKS_UPLOADED).withDescription(BLOCKS_UPLOADED_DESC));
        blocksHashMismatch = metrics.getOrCreate(new LongGauge.Config(perNodeMetricCategory, BLOCKS_HASH_MISMATCH)
                .withDescription(BLOCKS_HASH_MISMATCH_DESC));

        for (final var provider : bucketProviders) {
            final var perProviderPerNodeMetricCategory =
                    String.format(PER_PROVIDER_PER_NODE_METRIC_PREFIX, selfNodeId, provider);
            successfulUploads.put(
                    provider,
                    metrics.getOrCreate(new Counter.Config(perProviderPerNodeMetricCategory, UPLOADS_SUCCESS)
                            .withDescription(UPLOADS_SUCCESS_DESC)));

            failedUploads.put(
                    provider,
                    metrics.getOrCreate(new Counter.Config(perProviderPerNodeMetricCategory, UPLOADS_FAILURE)
                            .withDescription(UPLOADS_FAILURE_DESC)));

            hashMismatches.put(
                    provider,
                    metrics.getOrCreate(new Counter.Config(perProviderPerNodeMetricCategory, HASH_MISMATCH)
                            .withDescription(HASH_MISMATCH_DESC)));

            uploadLatencies.put(
                    provider,
                    new UploadLatencyMetric(
                            metrics.getOrCreate(new DurationGauge.Config(
                                            perProviderPerNodeMetricCategory, UPLOAD_LATENCY_LATEST, ChronoUnit.MILLIS)
                                    .withDescription("Latest upload latency for " + provider + " bucket")),
                            metrics.getOrCreate(new IntegerPairAccumulator.Config<>(
                                            perProviderPerNodeMetricCategory,
                                            UPLOAD_LATENCY_AVG,
                                            Integer.class,
                                            AVERAGE)
                                    .withDescription("Average upload latency for " + provider + " bucket")
                                    .withUnit("ms"))));
        }
    }

    /**
     * Update the metric for the current number of blocks retained in root block file directory.
     *
     * @param blocksRetainedCount current number of blocks retained on disk
     */
    public void updateBlocksRetainedCount(
            final long
                    blocksRetainedCount) { // FUTURE: Once https://github.com/hashgraph/hedera-services/pull/17135/ is
        // merged, invoke at the end of the BucketUploadManager.onBlockClosed()
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
    public void updateBlocksUploadedCount(
            final long
                    blocksUploadedCount) { // FUTURE: Once https://github.com/hashgraph/hedera-services/pull/17135/ is
        // merged, invoke at the end of the BucketUploadManager.onBlockClosed()
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
    public void updateBlocksHashMismatchCount(
            final long
                    blocksHashMismatchCount) { // FUTURE: Once https://github.com/hashgraph/hedera-services/pull/17135/
        // is merged, invoke at the end of the
        // BucketUploadManager.onBlockClosed()
        if (blocksHashMismatchCount < 0) {
            log.warn("Received number of hash mismatched blocks: {}", blocksHashMismatchCount);
        } else {
            blocksHashMismatch.set(blocksHashMismatchCount);
        }
    }

    /**
     * Update the metric for the number of successful block uploads per bucket provider.
     *
     * @param provider the provider of the bucket
     */
    public void incrementSuccessfulUploads(
            final String provider) { // FUTURE: Once https://github.com/hashgraph/hedera-services/pull/17135/ is merged,
        // invoke in BucketUploadManager.uploadToProvider()
        if (!successfulUploads.containsKey(provider)) {
            log.warn("Bucket provider {} not found for {} metric", provider, UPLOADS_SUCCESS);
        } else {
            successfulUploads.get(provider).increment();
        }
    }

    /**
     * Update the metric for the number of failed block uploads per bucket provider.
     *
     * @param provider the provider of the bucket
     */
    public void incrementFailedUploads(
            final String provider) { // FUTURE: Once https://github.com/hashgraph/hedera-services/pull/17135/ is merged,
        // invoke in BucketUploadManager.processBlockClosure()
        if (!failedUploads.containsKey(provider)) {
            log.warn("Bucket provider {} not found for {} metric", provider, UPLOADS_FAILURE);
        } else {
            failedUploads.get(provider).increment();
        }
    }

    /**
     * Update the metric for the number of hash mismatched blocks per bucket provider.
     *
     * @param provider the provider of the bucket
     */
    public void incrementHashMismatchedBlocks(
            final String provider) { // FUTURE: Once https://github.com/hashgraph/hedera-services/pull/17135/ is merged,
        // invoke in BucketUploadManager.uploadToProvider()
        if (!hashMismatches.containsKey(provider)) {
            log.warn("Bucket provider {} not found for {} metric", provider, HASH_MISMATCH);
        } else {
            hashMismatches.get(provider).increment();
        }
    }

    /**
     * Update the metrics for the upload latency for a bucket provider.
     *
     * @param provider the provider of the bucket
     * @param latency the latest upload latency
     */
    public void updateUploadLatency(
            final String provider,
            final Duration
                    latency) { // FUTURE: Once https://github.com/hashgraph/hedera-services/pull/17135/ is merged,
        // invoke in BucketUploadManager.uploadToProvider()
        if (!uploadLatencies.containsKey(provider)) {
            log.warn("Bucket provider {} not found for upload latency metrics", provider);
        } else {
            final var metric = uploadLatencies.get(provider);
            metric.latest().set(latency);
            metric.avg().update((int) latency.toMillis(), 1);
        }
    }
}
