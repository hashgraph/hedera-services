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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.swirlds.metrics.api.LongGauge;
import com.swirlds.metrics.api.Metrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class BlockStreamBucketUploaderMetricsTest {
    @Mock
    private Metrics metrics;

    @Mock
    private LongGauge longGauge;

    @LoggingSubject
    private BlockStreamBucketUploaderMetrics blockStreamBucketUploaderMetrics;

    @LoggingTarget
    private LogCaptor logCaptor;

    @BeforeEach
    void setUp() {
        when(metrics.getOrCreate(any(LongGauge.Config.class))).thenReturn(longGauge);
        long selfNodeId = 1L;
        blockStreamBucketUploaderMetrics = new BlockStreamBucketUploaderMetrics(metrics, selfNodeId);
    }

    @Test
    void testUpdateBlocksRetainedCount() {
        blockStreamBucketUploaderMetrics.updateBlocksRetainedCount(10L);
        verify(longGauge).set(10L);
    }

    @Test
    void testUpdateBlocksRetainedCountWithNegativeValue() {
        blockStreamBucketUploaderMetrics.updateBlocksRetainedCount(-1L);

        assertThat(logCaptor.warnLogs()).contains("Received number of retained blocks: -1");
        verify(longGauge, never()).set(anyLong());
    }

    @Test
    void testUpdateBlocksUploadedCount() {
        blockStreamBucketUploaderMetrics.updateBlocksUploadedCount(10L);
        verify(longGauge).set(10L);
    }

    @Test
    void testUpdateBlocksUploadedCountWithNegativeValue() {
        blockStreamBucketUploaderMetrics.updateBlocksUploadedCount(-1L);

        assertThat(logCaptor.warnLogs()).contains("Received number of uploaded blocks: -1");
        verify(longGauge, never()).set(anyLong());
    }

    @Test
    void testUpdateBlocksHashMismatchCount() {
        blockStreamBucketUploaderMetrics.updateBlocksHashMismatchCount(10L);
        verify(longGauge).set(10L);
    }

    @Test
    void testUpdateBlockHashMismatchCountWithNegativeValue() {
        blockStreamBucketUploaderMetrics.updateBlocksHashMismatchCount(-1L);

        assertThat(logCaptor.warnLogs()).contains("Received number of hash mismatched blocks: -1");
        verify(longGauge, never()).set(anyLong());
    }
}
