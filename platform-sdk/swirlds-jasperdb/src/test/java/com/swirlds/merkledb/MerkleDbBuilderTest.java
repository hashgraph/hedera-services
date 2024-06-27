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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.utility.LegacyTemporaryFileBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.merkledb.test.fixtures.ExampleFixedSizeVirtualValue;
import com.swirlds.merkledb.test.fixtures.ExampleFixedSizeVirtualValueSerializer;
import com.swirlds.merkledb.test.fixtures.ExampleLongKeyFixedSize;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerkleDbBuilderTest {

    private static Path testDirectory;

    private static final KeySerializer<ExampleLongKeyFixedSize> KEY_SERIALIZER =
            new ExampleLongKeyFixedSize.Serializer();

    private static final ValueSerializer<ExampleFixedSizeVirtualValue> VALUE_SERIALIZER =
            new ExampleFixedSizeVirtualValueSerializer();

    private static final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> TABLE_CONFIG = createTableConfig();

    private static MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> createTableConfig() {
        return new MerkleDbTableConfig<>(
                (short) 1, DigestType.SHA_384,
                (short) 1, KEY_SERIALIZER,
                (short) 1, VALUE_SERIALIZER);
    }

    @BeforeAll
    static void setup() throws IOException {
        testDirectory = LegacyTemporaryFileBuilder.buildTemporaryFile("MerkleDbBuilderTest");
    }

    @AfterEach
    public void afterCheckNoDbLeftOpen() {
        // check db count
        assertEquals(0, MerkleDbDataSource.getCountOfOpenDatabases(), "Expected no open dbs");
    }

    @Test
    @DisplayName("Test table config is passed to data source")
    public void testTableConfig() throws IOException {
        final MerkleDbDataSourceBuilder<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> builder =
                new MerkleDbDataSourceBuilder<>(KEY_SERIALIZER, VALUE_SERIALIZER, TABLE_CONFIG);
        VirtualDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource = null;
        try {
            dataSource = builder.build("test1", false);
            assertTrue(dataSource instanceof MerkleDbDataSource);
            MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> merkleDbDataSource =
                    (MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue>) dataSource;
            assertEquals(TABLE_CONFIG, merkleDbDataSource.getTableConfig());
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

    @Test
    @DisplayName("Test data source config defaults")
    public void testBuilderDefaults() throws IOException {
        final MerkleDbDataSourceBuilder<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> builder =
                new MerkleDbDataSourceBuilder<>(KEY_SERIALIZER, VALUE_SERIALIZER, TABLE_CONFIG);
        final MerkleDb defaultDatabase = MerkleDb.getDefaultInstance();
        VirtualDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource = null;
        try {
            dataSource = builder.build("test2", false);
            assertTrue(dataSource instanceof MerkleDbDataSource);
            MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> merkleDbDataSource =
                    (MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue>) dataSource;
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
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig =
                createTableConfig();
        tableConfig.preferDiskIndices(true).maxNumberOfKeys(1999).hashesRamToDiskThreshold(Integer.MAX_VALUE >> 4);
        final MerkleDbDataSourceBuilder<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> builder =
                new MerkleDbDataSourceBuilder<>(KEY_SERIALIZER, VALUE_SERIALIZER, tableConfig);
        final Path defaultDbPath = testDirectory.resolve("defaultDatabasePath");
        MerkleDb.setDefaultPath(defaultDbPath);
        VirtualDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource = null;
        try {
            dataSource = builder.build("test3", true);
            assertTrue(dataSource instanceof MerkleDbDataSource);
            MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> merkleDbDataSource =
                    (MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue>) dataSource;
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
