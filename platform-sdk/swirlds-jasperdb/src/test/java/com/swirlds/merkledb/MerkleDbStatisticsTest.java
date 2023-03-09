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

package com.swirlds.merkledb;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.merkledb.MerkleDbStatistics.STAT_CATEGORY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.test.fixtures.config.TestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerkleDbStatisticsTest {

    private static final String LABEL = "LaBeL";

    private MetricsConfig metricsConfig;
    private MerkleDbStatistics statistics;
    private Metrics metrics;

    @BeforeEach
    void setupService() {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        metricsConfig = configuration.getConfigData(MetricsConfig.class);

        final MetricKeyRegistry registry = mock(MetricKeyRegistry.class);
        when(registry.register(any(), any(), any())).thenReturn(true);
        metrics = new DefaultMetrics(
                null, registry, mock(ScheduledExecutorService.class), new DefaultMetricsFactory(), metricsConfig);
        statistics = new MerkleDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
    }

    @Test
    void testInitialState() {
        assertDoesNotThrow(statistics::cycleInternalNodeWritesPerSecond);
        assertDoesNotThrow(statistics::cycleInternalNodeReadsPerSecond);
        assertDoesNotThrow(statistics::cycleLeafWritesPerSecond);
        assertDoesNotThrow(statistics::cycleLeafByKeyReadsPerSecond);
        assertDoesNotThrow(statistics::cycleLeafByPathReadsPerSecond);
        assertDoesNotThrow(() -> statistics.setInternalHashesStoreFileCount(42));
        assertDoesNotThrow(() -> statistics.setInternalHashesStoreTotalFileSizeInMB(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreFileCount(42));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreTotalFileSizeInMB(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafPathToHashKeyValueStoreFileCount(42));
        assertDoesNotThrow(() -> statistics.setLeafPathToHashKeyValueStoreTotalFileSizeInMB(Math.PI));
        assertDoesNotThrow(() -> statistics.setInternalHashesStoreSmallMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setInternalHashesStoreMediumMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setInternalHashesStoreLargeMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreSmallMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreMediumMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreLargeMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafPathToHashKeyValueStoreSmallMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafPathToHashKeyValueStoreMediumMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafPathToHashKeyValueStoreLargeMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setOffHeapMemoryInternalNodesListInMB(42));
        assertDoesNotThrow(() -> statistics.setOffHeapMemoryLeafNodesListInMB(42));
        assertDoesNotThrow(() -> statistics.setOffHeapMemoryKeyToPathListInMB(42));
        assertDoesNotThrow(() -> statistics.setOffHeapMemoryDataSourceInMB(42));
    }

    @Test
    void testNonLongKeyMode() {
        // given

        // then
        assertNull(metrics.getMetric(STAT_CATEGORY, "leafKeyToPathFileCount_" + LABEL));
        assertNull(metrics.getMetric(STAT_CATEGORY, "leafKeyToPathFileSizeMb_" + LABEL));
        assertNull(metrics.getMetric(STAT_CATEGORY, "leafKeyToPathSmallMergeTime_" + LABEL));
        assertNull(metrics.getMetric(STAT_CATEGORY, "leafKeyToPathMediumMergeTime_" + LABEL));
        assertNull(metrics.getMetric(STAT_CATEGORY, "leafKeyToPathLargeMergeTime_" + LABEL));

        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreFileCount(42));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreTotalFileSizeInMB(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreSmallMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreMediumMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreLargeMergeTime(Math.PI));
    }

    @Test
    void testConstructorWithNullParameter() {
        assertThrows(IllegalArgumentException.class, () -> new MerkleDbStatistics(null, true));
    }

    @Test
    void testRegisterWithNullParameter() {
        // given
        final MerkleDbStatistics statistics = new MerkleDbStatistics(LABEL, false);

        // then
        assertThrows(IllegalArgumentException.class, () -> statistics.registerMetrics(null));
    }

    @Test
    void testCycleInternalNodeWritesPerSecond() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalNodeWrites/s_" + LABEL);

        // when
        statistics.cycleInternalNodeWritesPerSecond();

        // then
        assertValueSet(metric);
    }

    private static void assertValueSet(final Metric metric) {
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testCycleInternalNodeReadsPerSecond() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalNodeReads/s_" + LABEL);

        // when
        statistics.cycleInternalNodeReadsPerSecond();

        // then
        assertValueSet(metric);
    }

    @Test
    void testCycleLeafWritesPerSecond() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafWrites/s_" + LABEL);

        // when
        statistics.cycleLeafWritesPerSecond();

        // then
        assertValueSet(metric);
    }

    @Test
    void testCycleLeafByKeyReadsPerSecond() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafByKeyReads/s_" + LABEL);

        // when
        statistics.cycleLeafByKeyReadsPerSecond();

        // then
        assertValueSet(metric);
    }

    @Test
    void testCycleLeafByPathReadsPerSecond() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafByPathReads/s_" + LABEL);

        // when
        statistics.cycleLeafByPathReadsPerSecond();

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetInternalHashesStoreFileCount() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalHashFileCount_" + LABEL);

        // when
        statistics.setInternalHashesStoreFileCount(42);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetInternalHashesStoreTotalFileSizeInMB() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalHashFileSizeMb_" + LABEL);

        // when
        statistics.setInternalHashesStoreTotalFileSizeInMB(42);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafKeyToPathStoreFileCount() {
        // given
        statistics = new MerkleDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafKeyToPathFileCount_" + LABEL);

        // when
        statistics.setLeafKeyToPathStoreFileCount(42);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafKeyToPathStoreTotalFileSizeInMB() {
        // given
        statistics = new MerkleDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafKeyToPathFileSizeMb_" + LABEL);

        // when
        statistics.setLeafKeyToPathStoreTotalFileSizeInMB(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafPathToHashKeyValueStoreFileCount() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVFileCount_" + LABEL);

        // when
        statistics.setLeafPathToHashKeyValueStoreFileCount(42);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafPathToHashKeyValueStoreTotalFileSizeInMB() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVFileSizeMb_" + LABEL);

        // when
        statistics.setLeafPathToHashKeyValueStoreTotalFileSizeInMB(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetInternalHashesStoreSmallMergeTime() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalHashSmallMergeTime_" + LABEL);

        // when
        statistics.setInternalHashesStoreSmallMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetInternalHashesStoreMediumMergeTime() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalHashMediumMergeTime_" + LABEL);

        // when
        statistics.setInternalHashesStoreMediumMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetInternalHashesStoreLargeMergeTime() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalHashLargeMergeTime_" + LABEL);

        // when
        statistics.setInternalHashesStoreLargeMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafKeyToPathStoreSmallMergeTime() {
        // given
        final MetricKeyRegistry registry = mock(MetricKeyRegistry.class);
        when(registry.register(any(), any(), any())).thenReturn(true);
        final Metrics metrics = new DefaultMetrics(
                null, registry, mock(ScheduledExecutorService.class), new DefaultMetricsFactory(), metricsConfig);
        final MerkleDbStatistics statistics = new MerkleDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);

        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafKeyToPathSmallMergeTime_" + LABEL);

        // when
        statistics.setLeafKeyToPathStoreSmallMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafKeyToPathStoreMediumMergeTime() {
        // given
        statistics = new MerkleDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafKeyToPathMediumMergeTime_" + LABEL);

        // when
        statistics.setLeafKeyToPathStoreMediumMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafKeyToPathStoreLargeMergeTime() {
        // given
        statistics = new MerkleDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafKeyToPathLargeMergeTime_" + LABEL);

        // when
        statistics.setLeafKeyToPathStoreLargeMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafPathToHashKeyValueStoreSmallMergeTime() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVSmallMergeTime_" + LABEL);

        // when
        statistics.setLeafPathToHashKeyValueStoreSmallMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafPathToHashKeyValueStoreMediumMergeTime() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVMediumMergeTime_" + LABEL);

        // when
        statistics.setLeafPathToHashKeyValueStoreMediumMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafPathToHashKeyValueStoreLargeMergeTime() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVLargeMergeTime_" + LABEL);

        // when
        statistics.setLeafPathToHashKeyValueStoreLargeMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    public void testOffHeapInternal() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "offHeapInternalMb_" + LABEL);

        // when
        statistics.setOffHeapMemoryInternalNodesListInMB(42);

        // then
        assertValueSet(metric);
    }

    @Test
    public void testOffHeapLeaf() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "offHeapLeafMb_" + LABEL);

        // when
        statistics.setOffHeapMemoryLeafNodesListInMB(42);

        // then
        assertValueSet(metric);
    }

    @Test
    public void testOffHeapKeyToPath() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "offHeapInternalMb_" + LABEL);

        // when
        statistics.setOffHeapMemoryKeyToPathListInMB(42);

        // then
        assertValueSet(metric);
    }

    @Test
    public void testOffHeadDataSource() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "offHeapDataSourceMb_" + LABEL);

        // when
        statistics.setOffHeapMemoryDataSourceInMB(42);

        // then
        assertValueSet(metric);
    }
}
