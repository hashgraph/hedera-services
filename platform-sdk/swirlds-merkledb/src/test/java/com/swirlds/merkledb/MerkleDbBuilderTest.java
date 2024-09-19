/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.utility.LegacyTemporaryFileBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerkleDbBuilderTest {

    private static Path testDirectory;

    @BeforeAll
    static void setup() throws IOException {
        testDirectory = LegacyTemporaryFileBuilder.buildTemporaryFile("MerkleDbBuilderTest", config());
    }

    @AfterEach
    public void afterCheckNoDbLeftOpen() {
        // check db count
        assertEquals(0, MerkleDbDataSource.getCountOfOpenDatabases(), "Expected no open dbs");
    }

    private MerkleDbTableConfig createTableConfig() {
        return new MerkleDbTableConfig((short) 1, DigestType.SHA_384, config().getConfigData(MerkleDbConfig.class));
    }

    @Test
    @DisplayName("Test table config is passed to data source")
    public void testTableConfig() throws IOException {
        final MerkleDbTableConfig tableConfig = createTableConfig();
        final MerkleDbDataSourceBuilder builder = new MerkleDbDataSourceBuilder(tableConfig, config());
        VirtualDataSource dataSource = null;
        try {
            dataSource = builder.build("test1", false);
            assertTrue(dataSource instanceof MerkleDbDataSource);
            MerkleDbDataSource merkleDbDataSource = (MerkleDbDataSource) dataSource;
            assertEquals(tableConfig, merkleDbDataSource.getTableConfig());
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

    @Test
    @DisplayName("Test data source config defaults")
    public void testBuilderDefaults() throws IOException {
        final MerkleDbTableConfig tableConfig = createTableConfig();
        final MerkleDbDataSourceBuilder builder = new MerkleDbDataSourceBuilder(tableConfig, config());
        final MerkleDb defaultDatabase = MerkleDb.getDefaultInstance(config());
        VirtualDataSource dataSource = null;
        try {
            dataSource = builder.build("test2", false);
            assertTrue(dataSource instanceof MerkleDbDataSource);
            MerkleDbDataSource merkleDbDataSource = (MerkleDbDataSource) dataSource;
            assertEquals(
                    defaultDatabase
                            .getStorageDir()
                            .resolve("tables")
                            .resolve("test2-" + merkleDbDataSource.getTableId()),
                    merkleDbDataSource.getStorageDir());

            final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
            final MerkleDbConfig merkleDbConfig = configuration.getConfigData(MerkleDbConfig.class);
            assertFalse(merkleDbDataSource.isPreferDiskBasedIndexes());
            assertEquals(merkleDbConfig.maxNumOfKeys(), merkleDbDataSource.getMaxNumberOfKeys());
            assertEquals(merkleDbConfig.hashesRamToDiskThreshold(), merkleDbDataSource.getHashesRamToDiskThreshold());
            // set explicitly above
            assertFalse(merkleDbDataSource.isCompactionEnabled());
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

    @Test
    @DisplayName("Test data source config overrides")
    public void testBuilderOverrides() throws IOException {
        final MerkleDbTableConfig tableConfig = createTableConfig();
        tableConfig.preferDiskIndices(true).maxNumberOfKeys(1999).hashesRamToDiskThreshold(Integer.MAX_VALUE >> 4);
        final MerkleDbDataSourceBuilder builder = new MerkleDbDataSourceBuilder(tableConfig, config());
        final Path defaultDbPath = testDirectory.resolve("defaultDatabasePath");
        MerkleDb.setDefaultPath(defaultDbPath);
        VirtualDataSource dataSource = null;
        try {
            dataSource = builder.build("test3", true);
            assertTrue(dataSource instanceof MerkleDbDataSource);
            MerkleDbDataSource merkleDbDataSource = (MerkleDbDataSource) dataSource;
            assertEquals(
                    defaultDbPath.resolve("tables").resolve("test3-" + merkleDbDataSource.getTableId()),
                    merkleDbDataSource.getStorageDir());
            assertTrue(merkleDbDataSource.isPreferDiskBasedIndexes());
            assertEquals(1999, merkleDbDataSource.getMaxNumberOfKeys());
            assertEquals(Integer.MAX_VALUE >> 4, merkleDbDataSource.getHashesRamToDiskThreshold());
            // set explicitly above
            assertTrue(merkleDbDataSource.isCompactionEnabled());
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }
}
