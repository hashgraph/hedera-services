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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class MerkleDbTest {

    @BeforeAll
    public static void setup() throws Exception {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.merkledb");
    }

    @AfterEach
    public void shutdownTest() throws Exception {
        // check db count
        assertEquals(0, MerkleDbDataSource.getCountOfOpenDatabases(), "Expected no open dbs");
    }

    private static MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> fixedConfig() {
        return new MerkleDbTableConfig<>(
                (short) 1, DigestType.SHA_384,
                (short) 1, new ExampleLongKeyFixedSize.Serializer(),
                (short) 1, new ExampleFixedSizeVirtualValueSerializer());
    }

    @Test
    @DisplayName("Multiple calls to MerkleDb.getInstance()")
    public void testDoubleGetInstance() {
        final MerkleDb instance1 = MerkleDb.getDefaultInstance();
        Assertions.assertNotNull(instance1);
        final MerkleDb instance2 = MerkleDb.getDefaultInstance();
        Assertions.assertNotNull(instance2);
        Assertions.assertEquals(instance2, instance1);
    }

    @Test
    @DisplayName("Set custom default storage path")
    public void testChangeDefaultPath() throws IOException {
        final MerkleDb instance1 = MerkleDb.getDefaultInstance();
        Assertions.assertNotNull(instance1);
        final Path tempDir = TemporaryFileBuilder.buildTemporaryFile();
        MerkleDb.setDefaultPath(tempDir);
        final MerkleDb instance2 = MerkleDb.getDefaultInstance();
        Assertions.assertNotNull(instance2);
        Assertions.assertNotEquals(instance2, instance1);
        Assertions.assertEquals(tempDir, instance2.getStorageDir());
    }

    @Test
    @DisplayName("MerkleDb paths")
    public void testMerkleDbDirs() throws IOException {
        final Path tempDir = TemporaryFileBuilder.buildTemporaryFile();
        MerkleDb.setDefaultPath(tempDir);
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        Assertions.assertEquals(tempDir, instance.getStorageDir());
        // This is not black-box testing, but is it worth exposing MerkleDb.SHARED_DIR as public or
        // package-private just for testing purposes?
        Assertions.assertEquals(tempDir.resolve("shared"), instance.getSharedDir());
        // same here
        Assertions.assertEquals(tempDir.resolve("tables"), instance.getTablesDir());
        // and here
        Assertions.assertTrue(Files.exists(tempDir.resolve("metadata.mdb")));
    }

    @Test
    @DisplayName("MerkleDb data source paths")
    public void testDataSourcePaths() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final String tableName = "tabley";
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource =
                instance.createDataSource(tableName, tableConfig, false);
        Assertions.assertNotNull(dataSource);
        final Path dbDir = instance.getStorageDir();
        final int tableId = dataSource.getTableId();
        Assertions.assertEquals(dbDir.resolve("tables/" + tableName + "-" + tableId), dataSource.getStorageDir());
        Assertions.assertEquals(
                dbDir.resolve("tables/" + tableName + "-" + tableId), instance.getTableDir(tableName, tableId));
        dataSource.close();
    }

    @Test
    @DisplayName("Call MerkleDb.createDataSource() twice")
    public void testDoubleCreateDataSource() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final String tableName = "tablez";
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource =
                instance.createDataSource(tableName, tableConfig, false);
        Assertions.assertNotNull(dataSource);
        Assertions.assertThrows(
                IllegalStateException.class, () -> instance.createDataSource(tableName, tableConfig, false));
        dataSource.close();
    }

    @Test
    @DisplayName("Check data source table config")
    public void testDataSourceConfig() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final String tableName = "tablea";
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource =
                instance.createDataSource(tableName, tableConfig, false);
        Assertions.assertNotNull(dataSource);
        final int tableId = dataSource.getTableId();
        Assertions.assertEquals(tableConfig, dataSource.getTableConfig());
        Assertions.assertEquals(tableConfig, instance.getTableConfig(tableId));
        dataSource.close();
        // Table config is preserved across data source close/reopen
        Assertions.assertNotNull(instance.getTableConfig(tableId));
    }

    @Test
    @DisplayName("Get table config with wrong ID")
    public void testWrongTableConfig() {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        Assertions.assertNull(instance.getTableConfig(-1));
        // Needs to be updated, if we support more than 256 tables
        Assertions.assertNull(instance.getTableConfig(256));
    }

    @Test
    @DisplayName("Get and create data source")
    public void testGetDataSource() throws IOException {
        final Path dbDir = TemporaryFileBuilder.buildTemporaryFile();
        MerkleDb.setDefaultPath(dbDir);
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final String tableName = "tableb";
        Assertions.assertThrows(IllegalStateException.class, () -> instance.getDataSource(tableName, false));
    }

    @Test
    @DisplayName("Get data source after close")
    public void testGetDataSourceAfterClose() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final String tableName = "tablec";
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource =
                instance.createDataSource(tableName, tableConfig, false);
        Assertions.assertNotNull(dataSource);
        dataSource.close();
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource2 =
                instance.getDataSource(tableName, false);
        Assertions.assertNotNull(dataSource2);
        Assertions.assertEquals(dataSource2, dataSource);
        Assertions.assertNotSame(dataSource2, dataSource);
        dataSource2.close();
    }

    @Test
    @DisplayName("Get data source after reload")
    public void testGetDataSourceAfterReload() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final String tableName = "tablec";
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource =
                instance.createDataSource(tableName, tableConfig, false);
        Assertions.assertNotNull(dataSource);

        final Path snapshotDir = TemporaryFileBuilder.buildTemporaryFile();
        instance.snapshot(snapshotDir);

        final MerkleDb instance2 = MerkleDb.getInstance(snapshotDir);
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource2 =
                instance2.getDataSource(tableName, false);
        Assertions.assertNotNull(dataSource2);
        Assertions.assertNotEquals(dataSource2, dataSource);

        dataSource.close();
        dataSource2.close();
    }

    @Test
    @DisplayName("Test MerkleDb snapshot all tables")
    public void testSnapshot() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final String tableName1 = "tabled1";
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource1 =
                instance.createDataSource(tableName1, tableConfig, false);
        Assertions.assertNotNull(dataSource1);
        final String tableName2 = "tabled2";
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource2 =
                instance.createDataSource(tableName2, tableConfig, false);
        Assertions.assertNotNull(dataSource2);

        final Path snapshotDir = TemporaryFileBuilder.buildTemporaryFile();
        instance.snapshot(snapshotDir);

        final MerkleDb instance2 = MerkleDb.getInstance(snapshotDir);
        Assertions.assertTrue(Files.exists(instance2.getTableDir(tableName1, dataSource1.getTableId())));
        Assertions.assertFalse(Files.exists(instance2.getTableDir("tabled", dataSource1.getTableId())));
        Assertions.assertTrue(Files.exists(instance2.getTableDir(tableName2, dataSource2.getTableId())));
        Assertions.assertFalse(Files.exists(instance2.getTableDir("tabled", dataSource2.getTableId())));
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> restored1 =
                instance2.getDataSource(tableName1, false);
        Assertions.assertEquals(tableConfig, restored1.getTableConfig());
        restored1.close();
        Assertions.assertEquals(tableConfig, instance2.getTableConfig(restored1.getTableId()));
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> restored2 =
                instance2.getDataSource(tableName2, false);
        Assertions.assertEquals(tableConfig, restored2.getTableConfig());
        Assertions.assertEquals(tableConfig, instance2.getTableConfig(restored2.getTableId()));
        restored2.close();

        dataSource1.close();
        dataSource2.close();
    }

    @Test
    @DisplayName("Test MerkleDb snapshot some tables")
    public void testSnapshotSelectedTables() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final String tableName1 = "tablee1";
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource1 =
                instance.createDataSource(tableName1, tableConfig, false);
        Assertions.assertNotNull(dataSource1);
        final String tableName2 = "tablee2";
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource2 =
                instance.createDataSource(tableName2, tableConfig, false);
        Assertions.assertNotNull(dataSource2);

        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> activeCopy =
                instance.copyDataSource(dataSource2, true);
        dataSource1.close();
        dataSource2.close();

        Assertions.assertEquals(tableName2, activeCopy.getTableName());
        Assertions.assertNotEquals(dataSource2.getStorageDir(), activeCopy.getStorageDir());
        Assertions.assertEquals(tableConfig, activeCopy.getTableConfig());
        activeCopy.close();
    }

    @Test
    @DisplayName("Test snapshot with data source copied")
    public void testSnapshotCopiedTables() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final String tableName1 = "tablee3";
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource1 =
                instance.createDataSource(tableName1, tableConfig, false);
        Assertions.assertNotNull(dataSource1);
        final String tableName2 = "tablee4";
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource2 =
                instance.createDataSource(tableName2, tableConfig, false);
        Assertions.assertNotNull(dataSource2);

        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> inactiveCopy1 =
                instance.copyDataSource(dataSource1, false);
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> activeCopy2 =
                instance.copyDataSource(dataSource2, true);

        final Path snapshotDir = TemporaryFileBuilder.buildTemporaryFile("testSnapshotCopiedTables");
        instance.snapshot(snapshotDir);

        final MerkleDb snapshotInstance = MerkleDb.getInstance(snapshotDir);
        Assertions.assertTrue(Files.exists(snapshotInstance.getTableDir(tableName1, dataSource1.getTableId())));
        Assertions.assertFalse(Files.exists(snapshotInstance.getTableDir(tableName1, inactiveCopy1.getTableId())));
        Assertions.assertFalse(Files.exists(snapshotInstance.getTableDir(tableName2, dataSource2.getTableId())));
        Assertions.assertTrue(Files.exists(snapshotInstance.getTableDir(tableName2, activeCopy2.getTableId())));

        dataSource1.close();
        dataSource2.close();
        inactiveCopy1.close();
        activeCopy2.close();
    }

    @Test
    @DisplayName("Get snapshot after data source close")
    public void testSnapshotAfterClose() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final String tableName = "tablef";
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource =
                instance.createDataSource(tableName, tableConfig, false);
        Assertions.assertNotNull(dataSource);
        dataSource.close();
        final Path snapshotDir = TemporaryFileBuilder.buildTemporaryFile();
        instance.snapshot(snapshotDir);
        final MerkleDb instance2 = MerkleDb.getInstance(snapshotDir);
        // If a data source is closed, it isn't included to snapshot by default
        Assertions.assertThrows(IllegalStateException.class, () -> instance2.getDataSource(tableName, false));
    }

    @Test
    @DisplayName("Restore from snapshot")
    public void testRestore() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final String tableName = "tableg";
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource =
                instance.createDataSource(tableName, tableConfig, false);
        Assertions.assertNotNull(dataSource);
        final Path snapshotDir = TemporaryFileBuilder.buildTemporaryFile();
        instance.snapshot(snapshotDir);
        final Path newDir = TemporaryFileBuilder.buildTemporaryFile();
        MerkleDb.setDefaultPath(newDir);
        final MerkleDb instance2 = MerkleDb.restore(snapshotDir, null);
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource2 =
                instance2.getDataSource(tableName, false);
        Assertions.assertNotNull(dataSource2);
        Assertions.assertEquals(tableConfig, dataSource2.getTableConfig());

        dataSource.close();
        dataSource2.close();
    }

    @Test
    @DisplayName("Restore with no shared dir")
    public void testRestoreNoSharedDir() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final String tableName = "tableh";
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource =
                instance.createDataSource(tableName, tableConfig, false);
        Assertions.assertNotNull(dataSource);

        final Path snapshotDir = TemporaryFileBuilder.buildTemporaryFile();
        instance.snapshot(snapshotDir);
        // Make sure the instance can be restored even without the shared dir. In the future this
        // test may need to be removed, when the shared folder becomes mandatory to exist
        Files.delete(snapshotDir.resolve("shared"));

        final Path newDir = TemporaryFileBuilder.buildTemporaryFile();
        final MerkleDb instance2 = MerkleDb.restore(snapshotDir, newDir);
        Assertions.assertTrue(Files.exists(instance2.getSharedDir()));

        dataSource.close();
    }

    @Test
    @DisplayName("Double snapshots")
    public void testDoubleSnapshot() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final String tableName = "tablei";
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig = fixedConfig();
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource =
                instance.createDataSource(tableName + "1", tableConfig, false);
        Assertions.assertNotNull(dataSource);
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource2 =
                instance.createDataSource(tableName + "2", tableConfig, false);
        Assertions.assertNotNull(dataSource2);

        final Path snapshotDir = TemporaryFileBuilder.buildTemporaryFile();
        instance.snapshot(snapshotDir);
        // Restore again. This is what happens during signed state snapshots. From merkle tree
        // perspective, all virtual maps are serialized to snapshots separately. However, at
        // MerkleDb level, when the first virtual map (data source) is snapshot, the whole DB
        // is put to the target folder. For all subsequent virtual maps snapshots are no-ops,
        // MerkleDb just checks that the corresponding DB table is already there
        instance.snapshot(snapshotDir);

        final Path snapshotDir2 = TemporaryFileBuilder.buildTemporaryFile();
        instance.snapshot(snapshotDir2);
        // Remove one of the tables and try to restore again
        dataSource.close();
        instance.removeTable(dataSource.getTableId());
        Assertions.assertThrows(IllegalStateException.class, () -> instance.snapshot(snapshotDir2));

        dataSource2.close();
    }

    @Test
    @DisplayName("Test copied data sources are auto deleted on close")
    public void testCopiedDataSourceAutoDeleted() throws IOException {
        final MerkleDb instance = MerkleDb.getDefaultInstance();
        final String tableName1 = "tablej1";
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig1 = fixedConfig();
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource1 =
                instance.createDataSource(tableName1, tableConfig1, false);
        Assertions.assertNotNull(dataSource1);
        final String tableName2 = "tablej2";
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig2 = fixedConfig();
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> dataSource2 =
                instance.createDataSource(tableName2, tableConfig2, false);
        Assertions.assertNotNull(dataSource2);

        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> inactiveCopy1 =
                instance.copyDataSource(dataSource1, false);
        final MerkleDbDataSource<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> activeCopy2 =
                instance.copyDataSource(dataSource2, true);

        dataSource1.close();
        dataSource2.close();
        inactiveCopy1.close();
        activeCopy2.close();

        Assertions.assertTrue(Files.exists(instance.getTableDir(tableName1, dataSource1.getTableId())));
        Assertions.assertFalse(Files.exists(instance.getTableDir(tableName1, inactiveCopy1.getTableId())));
        Assertions.assertFalse(Files.exists(instance.getTableDir(tableName2, dataSource2.getTableId())));
        Assertions.assertTrue(Files.exists(instance.getTableDir(tableName2, activeCopy2.getTableId())));
    }
}
