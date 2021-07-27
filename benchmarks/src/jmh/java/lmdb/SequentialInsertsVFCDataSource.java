package lmdb;

import com.swirlds.common.crypto.Hash;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;

import java.io.IOException;

/**
 * Extended interface for VirtualDataSource's that support faster sequential data insertion where the keys are sequential
 */
public interface SequentialInsertsVFCDataSource<K extends VirtualKey, V extends VirtualValue> extends VirtualDataSource<K, V> {

    /**
     * Save a hash for a internal node
     *
     * @param path the path of the node to save hash for, if nothing has been stored for this path before it will be created.
     * @param hash a non-null hash to write
     */
    void saveInternalSequential(Object handle, long path, Hash hash);

    /**
     * Add a new leaf to store
     *
     * @param path the path for the new leaf
     * @param key the non-null key for the new leaf
     * @param value the value for new leaf, can be null
     * @param hash the non-null hash for new leaf
     * @throws IOException if there was a problem writing leaf
     */
    void addLeafSequential(Object handle, long path, K key, V value, Hash hash) throws IOException;
}
