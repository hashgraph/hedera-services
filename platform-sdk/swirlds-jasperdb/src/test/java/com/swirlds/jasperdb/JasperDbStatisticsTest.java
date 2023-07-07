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

package com.swirlds.jasperdb;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.jasperdb.JasperDbStatistics.STAT_CATEGORY;
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
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JasperDbStatisticsTest {

    private static final String LABEL = "LaBeL";

    private MetricsConfig metricsConfig;
    private JasperDbStatistics statistics;
    private Metrics metrics;

    @BeforeEach
    void setupService() {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        metricsConfig = configuration.getConfigData(MetricsConfig.class);

        final MetricKeyRegistry registry = mock(MetricKeyRegistry.class);
        when(registry.register(any(), any(), any())).thenReturn(true);
        metrics = new DefaultMetrics(
                null, registry, mock(ScheduledExecutorService.class), new DefaultMetricsFactory(), metricsConfig);
        statistics = new JasperDbStatistics(LABEL, false);
        statistics.registerMetrics(metrics);
    }

    @Test
    void testInitialState() {
        assertDoesNotThrow(statistics::cycleInternalNodeWritesPerSecond);
        assertDoesNotThrow(statistics::cycleInternalNodeReadsPerSecond);
        assertDoesNotThrow(statistics::cycleLeafWritesPerSecond);
        assertDoesNotThrow(statistics::cycleLeafByKeyReadsPerSecond);
        assertDoesNotThrow(statistics::cycleLeafByPathReadsPerSecond);
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreFileCount(42));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreTotalFileSizeInMB(Math.PI));
        assertDoesNotThrow(() -> statistics.setPathToKeyValueStoreSmallMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setPathToKeyValueStoreMediumMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setPathToKeyValueStoreLargeMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreSmallMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreMediumMergeTime(Math.PI));
        assertDoesNotThrow(() -> statistics.setLeafKeyToPathStoreLargeMergeTime(Math.PI));
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
        assertThrows(IllegalArgumentException.class, () -> new JasperDbStatistics(null, false));
    }

    @Test
    void testRegisterWithNullParameter() {
        // given
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, false);

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
    void testSetHashesStoreFileCount() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalHashFileCount_" + LABEL);

        // when
        statistics.setPathToHashStoreFileCount(42);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetHashesStoreTotalFileSizeInMB() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalHashFileSizeMb_" + LABEL);

        // when
        statistics.setPathToHashTotalFileSizeInMB(42);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafKeyToPathStoreFileCount() {
        // given
        statistics = new JasperDbStatistics(LABEL, true);
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
        statistics = new JasperDbStatistics(LABEL, true);
        statistics.registerMetrics(metrics);
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafKeyToPathFileSizeMb_" + LABEL);

        // when
        statistics.setLeafKeyToPathStoreTotalFileSizeInMB(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafPathToKeyValueStoreFileCount() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVFileCount_" + LABEL);

        // when
        statistics.setPathToKeyValueStoreFileCount(42);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafPathToKeyValueStoreTotalFileSizeInMB() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVFileSizeMb_" + LABEL);

        // when
        statistics.setPathToKeyValueStoreTotalFileSizeInMB(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetHashesStoreSmallMergeTime() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalHashSmallMergeTime_" + LABEL);

        // when
        statistics.setPathToHashSmallMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetHashesStoreMediumMergeTime() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalHashMediumMergeTime_" + LABEL);

        // when
        statistics.setPathToHashMediumMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetHashesStoreLargeMergeTime() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "internalHashLargeMergeTime_" + LABEL);

        // when
        statistics.setPathToHashLargeMergeTime(Math.PI);

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
        final JasperDbStatistics statistics = new JasperDbStatistics(LABEL, true);
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
        statistics = new JasperDbStatistics(LABEL, true);
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
        statistics = new JasperDbStatistics(LABEL, true);
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
        statistics.setPathToKeyValueStoreSmallMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafPathToHashKeyValueStoreMediumMergeTime() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVMediumMergeTime_" + LABEL);

        // when
        statistics.setPathToKeyValueStoreMediumMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }

    @Test
    void testSetLeafPathToHashKeyValueStoreLargeMergeTime() {
        // given
        final Metric metric = metrics.getMetric(STAT_CATEGORY, "leafHKVLargeMergeTime_" + LABEL);

        // when
        statistics.setPathToKeyValueStoreLargeMergeTime(Math.PI);

        // then
        assertValueSet(metric);
    }
}
