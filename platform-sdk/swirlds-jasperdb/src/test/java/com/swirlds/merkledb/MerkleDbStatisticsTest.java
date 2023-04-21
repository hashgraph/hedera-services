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
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
        statistics = new MerkleDbStatistics(LABEL);
        statistics.registerMetrics(metrics);
    }

    @Test
    void testInitialState() {
        assertDoesNotThrow(statistics::countHashWrites);
        assertDoesNotThrow(statistics::countHashReads);
        assertDoesNotThrow(statistics::countLeafWrites);
        assertDoesNotThrow(statistics::countLeafReads);
        assertDoesNotThrow(statistics::countLeafKeyReads);
        assertDoesNotThrow(() -> statistics.setHashesStoreFileCount(42));
        assertDoesNotThrow(() -> statistics.setHashesStoreFileSizeMb(31415));
        assertDoesNotThrow(() -> statistics.setLeafKeysStoreFileCount(42));
        assertDoesNotThrow(() -> statistics.setLeafKeysStoreFileSizeMb(31415));
        assertDoesNotThrow(() -> statistics.setLeavesStoreFileCount(42));
        assertDoesNotThrow(() -> statistics.setLeavesStoreFileSizeMb(31415));
        assertDoesNotThrow(() -> statistics.setTotalFileSizeMb(314159));
        assertDoesNotThrow(() -> statistics.setHashesStoreCompactionTimeMs(CompactionType.SMALL, 314));
        assertDoesNotThrow(() -> statistics.setHashesStoreCompactionSavedSpaceMb(CompactionType.SMALL, Math.PI));
        assertDoesNotThrow(() -> statistics.setHashesStoreCompactionTimeMs(CompactionType.MEDIUM, 3141));
        assertDoesNotThrow(() -> statistics.setHashesStoreCompactionSavedSpaceMb(CompactionType.MEDIUM, Math.PI));
        assertDoesNotThrow(() -> statistics.setHashesStoreCompactionTimeMs(CompactionType.FULL, 31415));
        assertDoesNotThrow(() -> statistics.setHashesStoreCompactionSavedSpaceMb(CompactionType.FULL, Math.PI));
        assertDoesNotThrow(() -> statistics.setLeavesStoreCompactionTimeMs(CompactionType.SMALL, 314));
        assertDoesNotThrow(() -> statistics.setLeavesStoreCompactionSavedSpaceMb(CompactionType.SMALL, Math.PI));
        assertDoesNotThrow(() -> statistics.setLeavesStoreCompactionTimeMs(CompactionType.MEDIUM, 3141));
        assertDoesNotThrow(() -> statistics.setLeavesStoreCompactionSavedSpaceMb(CompactionType.MEDIUM, Math.PI));
        assertDoesNotThrow(() -> statistics.setLeavesStoreCompactionTimeMs(CompactionType.FULL, 31415));
        assertDoesNotThrow(() -> statistics.setLeavesStoreCompactionSavedSpaceMb(CompactionType.FULL, Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeysStoreCompactionTimeMs(CompactionType.SMALL, 314));
        assertDoesNotThrow(() -> statistics.setLeafKeysStoreCompactionSavedSpaceMb(CompactionType.SMALL, Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeysStoreCompactionTimeMs(CompactionType.MEDIUM, 3141));
        assertDoesNotThrow(() -> statistics.setLeafKeysStoreCompactionSavedSpaceMb(CompactionType.MEDIUM, Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeysStoreCompactionTimeMs(CompactionType.FULL, 31415));
        assertDoesNotThrow(() -> statistics.setLeafKeysStoreCompactionSavedSpaceMb(CompactionType.FULL, Math.PI));
        assertDoesNotThrow(() -> statistics.setOffHeapHashesIndexMb(42));
        assertDoesNotThrow(() -> statistics.setOffHeapLeavesIndexMb(42));
        assertDoesNotThrow(() -> statistics.setOffHeapLongKeysIndexMb(42));
        assertDoesNotThrow(() -> statistics.setOffHeapObjectKeyBucketsIndexMb(42));
        assertDoesNotThrow(() -> statistics.setOffHeapHashesListMb(42));
        assertDoesNotThrow(() -> statistics.setOffHeapDataSourceMb(42));
    }

    @Test
    void testConstructorWithNullParameter() {
        assertThrows(IllegalArgumentException.class, () -> new MerkleDbStatistics(null));
    }

    @Test
    void testRegisterWithNullParameter() {
        // given
        final MerkleDbStatistics statistics = new MerkleDbStatistics(LABEL);

        // then
        assertThrows(IllegalArgumentException.class, () -> statistics.registerMetrics(null));
    }

    private Metric getMetric(final String section, final String suffix) {
        return getMetric(metrics, section, suffix);
    }

    private Metric getMetric(final Metrics metrics, final String section, final String suffix) {
        return metrics.getMetric(STAT_CATEGORY, "ds_" + section + suffix);
    }

    private static void assertValueSet(final Metric metric) {
        assertNotEquals(0.0, metric.get(VALUE));
    }

    @Test
    void testCycleInternalNodeWritesPerSecond() {
        // given
        final Metric metric = getMetric("queries_", "hashWrites/s_" + LABEL);
        // when
        statistics.countHashWrites();
        // then
        assertValueSet(metric);
    }

    @Test
    void testCycleInternalNodeReadsPerSecond() {
        // given
        final Metric metric = getMetric("queries_", "hashReads/s_" + LABEL);
        // when
        statistics.countHashReads();
        // then
        assertValueSet(metric);
    }

    @Test
    void testCycleLeafWritesPerSecond() {
        // given
        final Metric metric = getMetric("queries_", "leafWrites/s_" + LABEL);
        // when
        statistics.countLeafWrites();
        // then
        assertValueSet(metric);
    }

    @Test
    void testCycleLeafByKeyReadsPerSecond() {
        // given
        final Metric metric = getMetric("queries_", "leafReads/s_" + LABEL);
        // when
        statistics.countLeafReads();
        // then
        assertValueSet(metric);
    }

    @Test
    void testCycleLeafKeyWritesPerSecond() {
        // given
        final Metric metric = getMetric("queries_", "leafKeyWrites/s_" + LABEL);
        // when
        statistics.countLeafKeyWrites();
        // then
        assertValueSet(metric);
    }

    @Test
    void testCycleLeafKeyReadsPerSecond() {
        // given
        final Metric metric = getMetric("queries_", "leafKeyReads/s_" + LABEL);
        // when
        statistics.countLeafKeyReads();
        // then
        assertValueSet(metric);
    }

    @Test
    void testSetHashesStoreFileCount() {
        // given
        final Metric metric = getMetric("files_", "hashesStoreFileCount_" + LABEL);
        // when
        statistics.setHashesStoreFileCount(42);
        // then
        assertValueSet(metric);
    }

    @Test
    void testSetHashesStoreTotalFileSizeMb() {
        // given
        final Metric metric = getMetric("files_", "hashesStoreFileSizeMb_" + LABEL);
        // when
        statistics.setHashesStoreFileSizeMb(42);
        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeavesStoreFileCount() {
        // given
        final Metric metric = getMetric("files_", "leavesStoreFileCount_" + LABEL);
        // when
        statistics.setLeavesStoreFileCount(42);
        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeavesStoreTotalFileSizeMb() {
        // given
        final Metric metric = getMetric("files_", "leavesStoreFileSizeMb_" + LABEL);
        // when
        statistics.setLeavesStoreFileSizeMb(31415);
        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafKeysStoreFileCount() {
        // given
        statistics = new MerkleDbStatistics(LABEL);
        statistics.registerMetrics(metrics);
        final Metric metric = getMetric("files_", "leafKeysStoreFileCount_" + LABEL);
        // when
        statistics.setLeafKeysStoreFileCount(42);
        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafKeysStoreTotalFileSizeMb() {
        // given
        statistics = new MerkleDbStatistics(LABEL);
        statistics.registerMetrics(metrics);
        final Metric metric = getMetric("files_", "leafKeysStoreFileSizeMb_" + LABEL);
        // when
        statistics.setLeafKeysStoreFileSizeMb(31415);
        // then
        assertValueSet(metric);
    }

    private static String ctypeStr(final CompactionType c) {
        return switch (c) {
            case SMALL -> "Small";
            case MEDIUM -> "Medium";
            case FULL -> "Full";
        };
    }

    @ParameterizedTest
    @EnumSource(CompactionType.class)
    void testSetHashesStoreMergeTime(final CompactionType compactionType) {
        // given
        final Metric metric = getMetric("compactions_", "hashes" + ctypeStr(compactionType) + "TimeMs_" + LABEL);
        // when
        statistics.setHashesStoreCompactionTimeMs(compactionType, 31415);
        // then
        assertValueSet(metric);
    }

    @ParameterizedTest
    @EnumSource(CompactionType.class)
    void testSetHashesStoreSavedSpace(final CompactionType compactionType) {
        // given
        final Metric metric = getMetric("compactions_", "hashes" + ctypeStr(compactionType) + "SavedSpaceMb_" + LABEL);
        // when
        statistics.setHashesStoreCompactionSavedSpaceMb(compactionType, Math.PI);
        // then
        assertValueSet(metric);
    }

    @ParameterizedTest
    @EnumSource(CompactionType.class)
    void testSetLeafKeysStoreMergeTime(final CompactionType compactionType) {
        // given
        final MetricKeyRegistry registry = mock(MetricKeyRegistry.class);
        when(registry.register(any(), any(), any())).thenReturn(true);
        final Metrics metrics = new DefaultMetrics(
                null, registry, mock(ScheduledExecutorService.class), new DefaultMetricsFactory(), metricsConfig);
        final MerkleDbStatistics statistics = new MerkleDbStatistics(LABEL);
        statistics.registerMetrics(metrics);
        final Metric metric =
                getMetric(metrics, "compactions_", "leafKeys" + ctypeStr(compactionType) + "TimeMs_" + LABEL);
        // when
        statistics.setLeafKeysStoreCompactionTimeMs(compactionType, 31415);
        // then
        assertValueSet(metric);
    }

    @ParameterizedTest
    @EnumSource(CompactionType.class)
    void testSetLeafKeysStoreSavedSpace(final CompactionType compactionType) {
        // given
        final Metric metric =
                getMetric("compactions_", "leafKeys" + ctypeStr(compactionType) + "SavedSpaceMb_" + LABEL);
        // when
        statistics.setLeafKeysStoreCompactionSavedSpaceMb(compactionType, Math.PI);
        // then
        assertValueSet(metric);
    }

    @ParameterizedTest
    @EnumSource(CompactionType.class)
    void testSetLeavesStoreMergeTime(final CompactionType compactionType) {
        // given
        final Metric metric = getMetric("compactions_", "leaves" + ctypeStr(compactionType) + "TimeMs_" + LABEL);
        // when
        statistics.setLeavesStoreCompactionTimeMs(compactionType, 31415);
        // then
        assertValueSet(metric);
    }

    @ParameterizedTest
    @EnumSource(CompactionType.class)
    void testSetLeavesStoreSavedSpace(final CompactionType compactionType) {
        // given
        final Metric metric = getMetric("compactions_", "leaves" + ctypeStr(compactionType) + "SavedSpaceMb_" + LABEL);
        // when
        statistics.setLeavesStoreCompactionSavedSpaceMb(compactionType, Math.PI);
        // then
        assertValueSet(metric);
    }

    @Test
    public void testOffHeapHashesIndex() {
        // given
        final Metric metric = getMetric("offheap_", "hashesIndexMb_" + LABEL);
        // when
        statistics.setOffHeapHashesIndexMb(42);
        // then
        assertValueSet(metric);
    }

    @Test
    public void testOffHeapLeavesIndex() {
        // given
        final Metric metric = getMetric("offheap_", "leavesIndexMb_" + LABEL);
        // when
        statistics.setOffHeapLeavesIndexMb(42);
        // then
        assertValueSet(metric);
    }

    @Test
    public void testOffHeapLongKeysIndex() {
        // given
        final Metric metric = getMetric("offheap_", "longKeysIndexMb_" + LABEL);
        // when
        statistics.setOffHeapLongKeysIndexMb(42);
        // then
        assertValueSet(metric);
    }

    @Test
    public void testOffHeapObjectKeyBucketsIndex() {
        // given
        final Metric metric = getMetric("offheap_", "objectKeyBucketsIndexMb_" + LABEL);
        // when
        statistics.setOffHeapObjectKeyBucketsIndexMb(42);
        // then
        assertValueSet(metric);
    }

    @Test
    public void testOffHeadDataSource() {
        // given
        final Metric metric = getMetric("offheap_", "dataSourceMb_" + LABEL);
        // when
        statistics.setOffHeapDataSourceMb(42);
        // then
        assertValueSet(metric);
    }
}
