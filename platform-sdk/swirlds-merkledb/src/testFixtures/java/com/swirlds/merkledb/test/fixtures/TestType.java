/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.test.fixtures;

import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.CONFIGURATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultPlatformMetrics;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.metrics.platform.PlatformMetricsFactoryImpl;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.merkledb.MerkleDbDataSource;
import com.swirlds.merkledb.MerkleDbStatistics;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafBytes;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Supports parameterized testing of {@link MerkleDbDataSource} with
 * both fixed- and variable-size data.
 *
 * <p>Used with JUnit's 'org.junit.jupiter.params.provider.EnumSource' annotation.
 */
public enum TestType {

    /** Parameterizes a test with fixed-size key and fixed-size data. */
    long_fixed(true),
    /** Parameterizes a test with fixed-size key and variable-size data. */
    long_variable(false),
    /** Parameterizes a test with fixed-size complex key and fixed-size data. */
    longLong_fixed(true),
    /** Parameterizes a test with fixed-size complex key and variable-size data. */
    longLong_variable(false),
    /** Parameterizes a test with variable-size key and fixed-size data. */
    variable_fixed(false),
    /** Parameterizes a test with variable-size key and variable-size data. */
    variable_variable(false);

    public final boolean fixedSize;

    private Metrics metrics = null;

    TestType(boolean fixedSize) {
        this.fixedSize = fixedSize;
    }

    public DataTypeConfig dataType() {
        return new DataTypeConfig(this);
    }

    public Metrics getMetrics() {
        if (metrics == null) {
            final Configuration CONFIGURATION = new TestConfigBuilder().getOrCreateConfig();
            MetricsConfig metricsConfig = CONFIGURATION.getConfigData(MetricsConfig.class);

            final MetricKeyRegistry registry = mock(MetricKeyRegistry.class);
            when(registry.register(any(), any(), any())).thenReturn(true);
            metrics = new DefaultPlatformMetrics(
                    null,
                    registry,
                    mock(ScheduledExecutorService.class),
                    new PlatformMetricsFactoryImpl(metricsConfig),
                    metricsConfig);
            MerkleDbStatistics statistics =
                    new MerkleDbStatistics(CONFIGURATION.getConfigData(MerkleDbConfig.class), "test");
            statistics.registerMetrics(metrics);
        }
        return metrics;
    }

    public class DataTypeConfig {

        private final TestType testType;

        public DataTypeConfig(TestType testType) {
            this.testType = testType;
        }

        public Bytes createVirtualLongKey(final int i) {
            switch (testType) {
                default:
                case long_fixed:
                case long_variable:
                    return ExampleLongKey.longToKey(i);
                case longLong_fixed:
                case longLong_variable:
                    return ExampleLongLongKey.longToKey(i);
                case variable_fixed:
                case variable_variable:
                    return ExampleVariableKey.longToKey(i);
            }
        }

        public Bytes createVirtualValue(final int i) {
            switch (testType) {
                default:
                case long_fixed:
                case longLong_fixed:
                case variable_fixed:
                    return ExampleFixedValue.intToValue(i);
                case long_variable:
                case longLong_variable:
                case variable_variable:
                    return ExampleVariableValue.intToValue(i);
            }
        }

        /**
         * Get the file size for a file created in DataFileLowLevelTest.createFile test. Values here are measured values
         * from a known good test run.
         */
        public long getDataFileLowLevelTestFileSize() {
            switch (testType) {
                default:
                case long_fixed:
                case long_variable:
                case longLong_fixed:
                case longLong_variable:
                case variable_fixed:
                case variable_variable:
                    return 24576L;
            }
        }

        public MerkleDbDataSource createDataSource(
                final Path dbPath,
                final String name,
                final int size,
                final long hashesRamToDiskThreshold,
                final boolean enableMerging,
                boolean preferDiskBasedIndexes)
                throws IOException {
            final MerkleDb database = MerkleDb.getInstance(dbPath, CONFIGURATION);
            final MerkleDbConfig merkleDbConfig = database.getConfiguration().getConfigData(MerkleDbConfig.class);
            final MerkleDbTableConfig tableConfig = new MerkleDbTableConfig(
                            (short) 1,
                            DigestType.SHA_384,
                            merkleDbConfig.maxNumOfKeys(),
                            merkleDbConfig.hashesRamToDiskThreshold())
                    .preferDiskIndices(preferDiskBasedIndexes)
                    .maxNumberOfKeys(size * 10L)
                    .hashesRamToDiskThreshold(hashesRamToDiskThreshold);
            MerkleDbDataSource dataSource = database.createDataSource(name, tableConfig, enableMerging);
            dataSource.registerMetrics(getMetrics());
            return dataSource;
        }

        public MerkleDbDataSource getDataSource(final Path dbPath, final String name, final boolean enableMerging)
                throws IOException {
            final MerkleDb database = MerkleDb.getInstance(dbPath, CONFIGURATION);
            return database.getDataSource(name, enableMerging);
        }

        public VirtualHashRecord createVirtualInternalRecord(final int i) {
            return new VirtualHashRecord(i, MerkleDbTestUtils.hash(i));
        }

        public VirtualLeafBytes createVirtualLeafRecord(final int i) {
            return createVirtualLeafRecord(i, i, i);
        }

        public VirtualLeafBytes createVirtualLeafRecord(final long path, final int i, final int valueIndex) {

            switch (testType) {
                default:
                case long_fixed:
                case longLong_fixed:
                case variable_fixed:
                    return new VirtualLeafBytes(
                            path, createVirtualLongKey(i), ExampleFixedValue.intToValue(valueIndex));
                case long_variable:
                case longLong_variable:
                case variable_variable:
                    return new VirtualLeafBytes(
                            path, createVirtualLongKey(i), ExampleVariableValue.intToValue(valueIndex));
            }
        }
    }
}
