/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.app.utils.TestUtils;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.Metrics;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class BlockStreamBucketUploaderMetricsTest {
    @LoggingSubject
    private BlockStreamBucketUploaderMetrics blockStreamBucketUploaderMetrics;

    @LoggingTarget
    private LogCaptor logCaptor;

    private Metrics metrics;
    private static final long SELF_NODE_ID = 1L;

    @BeforeEach
    void setUp() {
        metrics = TestUtils.metrics();
        blockStreamBucketUploaderMetrics = new BlockStreamBucketUploaderMetrics(metrics, SELF_NODE_ID);
    }

    @Test
    void testUpdateBlocksRetainedCount() {
        blockStreamBucketUploaderMetrics.updateBlocksRetainedCount(10L);

        final var metricCategory = String.format(BlockStreamBucketUploaderMetrics.PER_NODE_METRIC_PREFIX, SELF_NODE_ID);
        final var metric = metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.BLOCKS_RETAINED);
        assertEquals(10L, metric.get(Metric.ValueType.VALUE));
    }

    @Test
    void testUpdateBlocksRetainedCountWithNegativeValue() {
        blockStreamBucketUploaderMetrics.updateBlocksRetainedCount(-1L);

        final var metricCategory = String.format(BlockStreamBucketUploaderMetrics.PER_NODE_METRIC_PREFIX, SELF_NODE_ID);
        final var metric = metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.BLOCKS_RETAINED);
        assertEquals(0L, metric.get(Metric.ValueType.VALUE));
        assertThat(logCaptor.warnLogs()).contains("Received number of retained blocks: -1");
    }

    @Test
    void testUpdateBlocksUploadedCount() {
        blockStreamBucketUploaderMetrics.updateBlocksUploadedCount(10L);

        final var metricCategory = String.format(BlockStreamBucketUploaderMetrics.PER_NODE_METRIC_PREFIX, SELF_NODE_ID);
        final var metric = metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.BLOCKS_UPLOADED);
        assertEquals(10L, metric.get(Metric.ValueType.VALUE));
    }

    @Test
    void testUpdateBlocksUploadedCountWithNegativeValue() {
        blockStreamBucketUploaderMetrics.updateBlocksUploadedCount(-1L);

        final var metricCategory = String.format(BlockStreamBucketUploaderMetrics.PER_NODE_METRIC_PREFIX, SELF_NODE_ID);
        final var metric = metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.BLOCKS_UPLOADED);
        assertEquals(0L, metric.get(Metric.ValueType.VALUE));
        assertThat(logCaptor.warnLogs()).contains("Received number of uploaded blocks: -1");
    }

    @Test
    void testUpdateBlocksHashMismatchCount() {
        blockStreamBucketUploaderMetrics.updateBlocksHashMismatchCount(10L);

        final var metricCategory = String.format(BlockStreamBucketUploaderMetrics.PER_NODE_METRIC_PREFIX, SELF_NODE_ID);
        final var metric = metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.BLOCKS_HASH_MISMATCH);
        assertEquals(10L, metric.get(Metric.ValueType.VALUE));
    }

    @Test
    void testUpdateBlockHashMismatchCountWithNegativeValue() {
        blockStreamBucketUploaderMetrics.updateBlocksHashMismatchCount(-1L);

        final var metricCategory = String.format(BlockStreamBucketUploaderMetrics.PER_NODE_METRIC_PREFIX, SELF_NODE_ID);
        final var metric = metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.BLOCKS_HASH_MISMATCH);
        assertEquals(0L, metric.get(Metric.ValueType.VALUE));
        assertThat(logCaptor.warnLogs()).contains("Received number of hash mismatched blocks: -1");
    }

    @Test
    void testIncrementSuccessfulUploads() {
        final var providers = List.of("aws", "gcp");
        for (final var provider : providers) {
            blockStreamBucketUploaderMetrics.incrementSuccessfulUploads(provider);
        }

        for (final var provider : providers) {
            final var metricCategory = String.format(
                    BlockStreamBucketUploaderMetrics.PER_PROVIDER_PER_NODE_METRIC_PREFIX, SELF_NODE_ID, provider);
            final var metric = metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.UPLOADS_SUCCESS);
            assertEquals(1L, metric.get(Metric.ValueType.VALUE));
        }
    }

    @Test
    void testIncrementFailedUploads() {
        final var providers = List.of("aws", "gcp");
        for (final var provider : providers) {
            blockStreamBucketUploaderMetrics.incrementFailedUploads(provider);
        }

        for (final var provider : providers) {
            final var metricCategory = String.format(
                    BlockStreamBucketUploaderMetrics.PER_PROVIDER_PER_NODE_METRIC_PREFIX, SELF_NODE_ID, provider);
            final var metric = metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.UPLOADS_FAILURE);
            assertEquals(1L, metric.get(Metric.ValueType.VALUE));
        }
    }

    @Test
    void testIncrementHashMismatches() {
        final var providers = List.of("aws", "gcp");
        for (final var provider : providers) {
            blockStreamBucketUploaderMetrics.incrementHashMismatchedBlocks(provider);
        }

        for (final var provider : providers) {
            final var metricCategory = String.format(
                    BlockStreamBucketUploaderMetrics.PER_PROVIDER_PER_NODE_METRIC_PREFIX, SELF_NODE_ID, provider);
            final var metric = metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.HASH_MISMATCH);
            assertEquals(1L, metric.get(Metric.ValueType.VALUE));
        }
    }

    @Test
    void testIncrementSuccessfulUploadsWithInvalidProvider() {
        final var invalidProvider = "invalid";
        blockStreamBucketUploaderMetrics.incrementSuccessfulUploads(invalidProvider);

        final var metricCategory = String.format(
                BlockStreamBucketUploaderMetrics.PER_PROVIDER_PER_NODE_METRIC_PREFIX, SELF_NODE_ID, invalidProvider);
        final var metric = metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.UPLOADS_SUCCESS);
        assertNull(metric);
        assertThat(logCaptor.warnLogs())
                .contains("Bucket provider " + invalidProvider + " not found for "
                        + BlockStreamBucketUploaderMetrics.UPLOADS_SUCCESS + " metric");
    }

    @Test
    void testIncrementFailedUploadsWithInvalidProvider() {
        final var invalidProvider = "invalid";
        blockStreamBucketUploaderMetrics.incrementFailedUploads(invalidProvider);

        final var metricCategory = String.format(
                BlockStreamBucketUploaderMetrics.PER_PROVIDER_PER_NODE_METRIC_PREFIX, SELF_NODE_ID, invalidProvider);
        final var metric = metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.UPLOADS_FAILURE);
        assertNull(metric);
        assertThat(logCaptor.warnLogs())
                .contains("Bucket provider " + invalidProvider + " not found for "
                        + BlockStreamBucketUploaderMetrics.UPLOADS_FAILURE + " metric");
    }

    @Test
    void testIncrementHashMismatchesWithInvalidProvider() {
        final var invalidProvider = "invalid";
        blockStreamBucketUploaderMetrics.incrementHashMismatchedBlocks(invalidProvider);

        final var metricCategory = String.format(
                BlockStreamBucketUploaderMetrics.PER_PROVIDER_PER_NODE_METRIC_PREFIX, SELF_NODE_ID, invalidProvider);
        final var metric = metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.HASH_MISMATCH);
        assertNull(metric);
        assertThat(logCaptor.warnLogs())
                .contains("Bucket provider " + invalidProvider + " not found for "
                        + BlockStreamBucketUploaderMetrics.HASH_MISMATCH + " metric");
    }

    @Test
    void testUpdateUploadLatency() {
        final var providers = List.of("aws", "gcp");
        final var latencies = List.of(Duration.ofMillis(1234), Duration.ofMillis(567));
        for (final var provider : providers) {
            for (final var latency : latencies) {
                blockStreamBucketUploaderMetrics.updateUploadLatency(provider, latency);
            }
        }

        final int expectedLatestLatencyMs = (int) latencies.getLast().toMillis();
        final int expectedAverageLatencyMs =
                (int) latencies.stream().mapToLong(Duration::toMillis).average().orElse(0);
        for (final var provider : providers) {
            final var metricCategory = String.format(
                    BlockStreamBucketUploaderMetrics.PER_PROVIDER_PER_NODE_METRIC_PREFIX, SELF_NODE_ID, provider);
            final var uploadLatencyLatest =
                    metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.UPLOAD_LATENCY_LATEST);
            final var uploadLatencyAvg =
                    metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.UPLOAD_LATENCY_AVG);
            assertEquals(
                    expectedLatestLatencyMs, ((Double) uploadLatencyLatest.get(Metric.ValueType.VALUE)).intValue());
            assertEquals(expectedAverageLatencyMs, uploadLatencyAvg.get(Metric.ValueType.VALUE));
        }
    }

    @Test
    void testUpdateUploadLatencyWithInvalidProvider() {
        final var invalidProvider = "invalid";
        blockStreamBucketUploaderMetrics.updateUploadLatency(invalidProvider, Duration.ofMillis(1234));

        final var metricCategory = String.format(
                BlockStreamBucketUploaderMetrics.PER_PROVIDER_PER_NODE_METRIC_PREFIX, SELF_NODE_ID, invalidProvider);
        final var uploadLatencyLatest =
                metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.UPLOAD_LATENCY_LATEST);
        final var uploadLatencyAvg =
                metrics.getMetric(metricCategory, BlockStreamBucketUploaderMetrics.UPLOAD_LATENCY_AVG);

        assertNull(uploadLatencyLatest);
        assertNull(uploadLatencyAvg);
        assertThat(logCaptor.warnLogs())
                .contains("Bucket provider " + invalidProvider + " not found for upload latency metrics");
    }
}
