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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Virtual data source builder that manages {@link MerkleDb} based data sources.
 *
 * One of the key MerkleDb builder config options is database directory. When a builder is
 * requested to create a new data source, or restore an existing data sources from snapshot,
 * the data source is hosted in the specified database. Full data source path is therefore
 * databaseDir + "/" + dataSource.label. To make sure there are no folder name conflicts
 * between data sources with the same label, e.g. on copy or snapshot, MerkleDb builders
 * use different database directories, usually managed using {@link TemporaryFileBuilder}.
 *
 * @param <K>
 *     Virtual key type
 * @param <V>
 *     Virtual value type
 */
public class MerkleDbDataSourceBuilder<K extends VirtualKey<? super K>, V extends VirtualValue>
        implements VirtualDataSourceBuilder<K, V> {

    private static final long CLASS_ID = 0x176ede0e1a69828L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * Default database folder used by this builder to create and restore data sources. If {@code null},
     * the default {@link MerkleDb} database is used.
     */
    private Path databaseDir;

    /**
     * Table configuration to use when this builder is requested to create a new data source.
     */
    private MerkleDbTableConfig<K, V> tableConfig;

    /**
     * Default constructor for deserialization purposes.
     */
    public MerkleDbDataSourceBuilder() {
        // for deserialization
    }

    /**
     * Creates a new data source builder with the specified table configuration.
     *
     * @param tableConfig
     *      Table configuration to use to create new data sources
     */
    public MerkleDbDataSourceBuilder(final MerkleDbTableConfig<K, V> tableConfig) {
        this(null, tableConfig);
    }

    /**
     * Creates a new data source builder with the specified database dir and table configuration.
     *
     * @param databaseDir
     *      Default database folder. May be {@code null}
     * @param tableConfig
     *      Table configuration to use to create new data sources
     */
    public MerkleDbDataSourceBuilder(final Path databaseDir, final MerkleDbTableConfig<K, V> tableConfig) {
        this.databaseDir = databaseDir;
        this.tableConfig = tableConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualDataSource<K, V> build(final String label, final boolean withDbCompactionEnabled) {
        if (tableConfig == null) {
            throw new IllegalArgumentException("Table serialization config is missing");
        }
        // Creates a new data source in this builder's database dir or in the default MerkleDb instance
        final MerkleDb database = MerkleDb.getInstance(databaseDir);
        try {
            return database.createDataSource(
                    label, // use VirtualMap name as the table name
                    tableConfig,
                    withDbCompactionEnabled);
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualDataSource<K, V> copy(final VirtualDataSource<K, V> snapshotMe, final boolean makeCopyActive) {
        if (!(snapshotMe instanceof MerkleDbDataSource<K, V> source)) {
            throw new IllegalArgumentException("The datasource must be compatible with the MerkleDb");
        }
        final String name = source.getTableName();
        try {
            return source.getDatabase().copyDataSource(source, makeCopyActive);
        } catch (final IOException z) {
            throw new UncheckedIOException(z);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void snapshot(final Path destination, final VirtualDataSource<K, V> snapshotMe) {
        if (!(snapshotMe instanceof MerkleDbDataSource<K, V> source)) {
            throw new IllegalArgumentException("The datasource must be compatible with the MerkleDb");
        }
        try {
            // Snapshot all tables. When this snapshot() method is called for other data sources,
            // the database will check if they are already present in the destination path. If so,
            // the snapshot will be a no-op
            source.getDatabase().snapshot(destination);
        } catch (final IOException z) {
            throw new UncheckedIOException(z);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualDataSource<K, V> restore(final String label, final Path source) {
        try {
            // Restore to the default database. Assuming the default database hasn't been initialized yet.
            // Note that all database data, shared and per-table for all tables, will be restored.
            final MerkleDb database = MerkleDb.restore(source, databaseDir);
            return database.getDataSource(label, true);
        } catch (final IOException z) {
            throw new UncheckedIOException(z);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        // The order of the first 3 fields matches JasperDbBuilder serialization
        out.writeSerializable(tableConfig, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        tableConfig = in.readSerializable(false, MerkleDbTableConfig::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return tableConfig.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof MerkleDbDataSourceBuilder<?, ?> that)) {
            return false;
        }
        return Objects.equals(tableConfig, that.tableConfig);
    }
}
