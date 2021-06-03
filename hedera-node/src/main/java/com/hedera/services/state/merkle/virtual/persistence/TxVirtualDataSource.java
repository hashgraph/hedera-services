package com.hedera.services.state.merkle.virtual.persistence;

import com.hedera.services.state.merkle.virtual.Path;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An implementation of {@link VirtualDataSource} which is transactional and allows for
 * both {@link #commit()} and {@link #rollback()} functionality. Typically, a
 * simpler non-transactional data source will be wrapped by a TxVirtualDataSource and
 * supplied to a {@link com.hedera.services.state.merkle.virtual.VirtualMap}.
 *
 * @param <K> The type of key
 * @param <V> The type of value
 */
public class TxVirtualDataSource<K, V> implements VirtualDataSource<K, V> {
    /**
     * The wrapped data source. Must not be null.
     */
    private final VirtualDataSource<K, V> ds;

    /**
     * A cache of records which have been modified, accessible by the key.
     * The same exact records are found in recordsByPath.
     */
    private Map<K, VirtualRecord<K, V>> recordsByKey = new HashMap<>();

    /**
     * A cache of records which have been modified, accessible by the path.
     * The same exact records are found in recordsByKey.
     */
    private Map<Path, VirtualRecord<K, V>> recordsByPath = new HashMap<>();

    /**
     * A map of records which have been deleted, accessible by the key.
     * The same exact records are in deletedRecordsByPath.
     */
    private Map<K, VirtualRecord<K, V>> deletedRecordsByKey = new HashMap<>();

    /**
     * A map of records which have been deleted, accessible by the path.
     * The same exact records are in deletedRecordsByPath.
     */
    private Map<Path, VirtualRecord<K, V>> deletedRecordsByPath = new HashMap<>();

    private Path firstLeafPath;
    private Path lastLeafPath;
    private boolean closed = false;

    /**
     * Creates a new Transactional data source, wrapping the actual data source.
     *
     * @param ds The wrapped datasource which must not be null.
     */
    public TxVirtualDataSource(VirtualDataSource<K, V> ds) {
        this.ds = Objects.requireNonNull(ds);
        this.firstLeafPath = ds.getFirstLeafPath();
        this.lastLeafPath = ds.getLastLeafPath();
    }

    /**
     * Writes the new state to disk. No state should be written until commit is called.
     * If commit is never called, the underlying persistent store should never be updated.
     * This implies that all updates must be buffered in memory. This is OK, since we
     * only update a few nodes per transaction.
     */
    public void commit() {
        // TODO throw if closed?

        // Save the leaf paths
        ds.writeFirstLeafPath(firstLeafPath);
        ds.writeLastLeafPath(lastLeafPath);

        // Any modified records must be written. It doesn't matter whether we write
        // the records in recordsByPath or recordsByKey, they both have the same
        // set of records.
        recordsByPath.values().forEach(ds::setRecord);

        // Any deleted records must be deleted. It doesn't matter whether we delete
        // the records in deletedRecordsByPath or deletedRecordsByKey, they both
        // contain the same set of records.
        deletedRecordsByPath.values().forEach(ds::deleteRecord);

        // Just reset all the state
        rollback();
    }

    /**
     * Rolls back the data source to the last committed state.
     */
    public void rollback() {
        // TODO Throw if closed?
        recordsByPath.clear();
        recordsByKey.clear();
        deletedRecordsByKey.clear();
        deletedRecordsByPath.clear();

        // load the leaf paths
        firstLeafPath = ds.getFirstLeafPath();
        lastLeafPath = ds.getLastLeafPath();
    }

    @Override
    public VirtualRecord<K, V> getRecord(K key) {
        // Check whether we have a modified record for this key. If so, return it.
        final var rec = recordsByKey.get(key);
        if (rec != null) {
            return rec;
        }

        // If we deleted the record, then return null.
        if (deletedRecordsByKey.containsKey(key)) {
            return null;
        }

        // It hasn't been modified or deleted, so we can just delegate to the data source
        return ds.getRecord(key);
    }

    @Override
    public VirtualRecord<K, V> getRecord(Path path) {
        // Check whether we have a modified record for this key. If so, return it.
        final var rec = recordsByPath.get(path);
        if (rec != null) {
            return rec;
        }

        // If we deleted the record, then return null.
        if (deletedRecordsByPath.containsKey(path)) {
            return null;
        }

        // It hasn't been modified or deleted, so we can just delegate to the data source
        return ds.getRecord(path);
    }

    @Override
    public void deleteRecord(VirtualRecord<K, V> record) {
        if (record != null) {
            // It may have been modified already, so we need to make sure to
            // delete it from these maps.
            recordsByPath.remove(record.getPath());
            recordsByKey.remove(record.getKey());

            // Record that it is now deleted.
            deletedRecordsByKey.put(record.getKey(), record);
            deletedRecordsByPath.put(record.getPath(), record);
        }
    }

    @Override
    public void setRecord(VirtualRecord<K, V> record) {
        if (record != null) {
            // Make sure this hasn't already been deleted. If so, we have a problem!
            if (deletedRecordsByPath.containsKey(record.getPath())) {
                throw new IllegalStateException("Cannot update a deleted record!");
            }

            recordsByPath.put(record.getPath(), record);
            recordsByKey.put(record.getKey(), record);
        }
    }

    @Override
    public void writeLastLeafPath(Path path) {
        this.lastLeafPath = path;
    }

    @Override
    public Path getLastLeafPath() {
        return lastLeafPath;
    }

    @Override
    public void writeFirstLeafPath(Path path) {
        this.firstLeafPath = path;
    }

    @Override
    public Path getFirstLeafPath() {
        return firstLeafPath;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        // TODO do I delegate close to the wrapped ds?
    }
}
