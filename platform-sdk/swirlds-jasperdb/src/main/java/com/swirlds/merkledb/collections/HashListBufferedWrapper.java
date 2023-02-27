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

package com.swirlds.merkledb.collections;

import com.swirlds.common.crypto.Hash;
import com.swirlds.merkledb.MerkleDbDataSource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A wrapper for a HashList that has two modes, direct pass though or an overlaid cache buffer.
 * <p>
 * We block all threads calling put methods while we are switching between modes.
 * </p>
 */
public class HashListBufferedWrapper implements HashList {

    private static final int PARALLELISM_THRESHOLD = 100_000;

    /** The HashList we are wrapping and providing an overlay cache to */
    private final HashList wrappedHashList;
    /** Atomic reference to overlay cache, reference can be to null if we are not currently using an overlay cache. */
    private final AtomicReference<ConcurrentHashMap<Long, Hash>> cachedChanges = new AtomicReference<>(null);
    /**
     * Indicates whether fresh changes should prefer being written directly to the {@link #wrappedHashList},
     * even if the cache exists. The only time when this is true that a change will still update the
     * overlay cache, is if the change is still in the cache and hasn't been written down to the
     * {@link #wrappedHashList} yet.
     */
    private final AtomicBoolean skipCacheOnWrite = new AtomicBoolean(false);

    /**
     * Create a new HashListBufferedWrapper wrapping wrappedHashList
     *
     * @param wrappedHashList
     * 		The HashList we are wrapping and providing an overlay cache to
     */
    public HashListBufferedWrapper(HashList wrappedHashList) {
        this.wrappedHashList = wrappedHashList;
    }

    /**
     * Closes this HashList and wrapped HashList freeing any resources used
     */
    @Override
    public void close() throws IOException {
        wrappedHashList.close();
    }

    /**
     * Set if we are in pass though or overlay mode
     *
     * <p><b>Important: it is require there be external locking to prevent this method and put methods being called at
     * the same time.</b></p>
     *
     * @param useOverlay
     * 		true puts us in overlay mode and false puts us in pass though mode.
     */
    public synchronized void setUseOverlay(boolean useOverlay) {
        final ConcurrentHashMap<Long, Hash> cache = cachedChanges.get();
        final boolean usingOverlayMode = cache != null;
        if (useOverlay == usingOverlayMode) {
            return;
        }
        if (usingOverlayMode) { // stop using overlay
            // write all cached values down to wrapped long list
            skipCacheOnWrite.set(true);
            cache.forEachKey(
                    PARALLELISM_THRESHOLD,
                    k -> cache.compute(k, (key, value) -> {
                        assert value != null : "We only iterate over known values and nobody else removes them";
                        wrappedHashList.put(key, value);
                        return null;
                    }));
            cachedChanges.set(null);
        } else { // start using cache
            skipCacheOnWrite.set(false);
            cachedChanges.set(new ConcurrentHashMap<>());
        }
    }

    /**
     * Get the {@link Hash} at the given index.
     *
     * @param index
     * 		the zero-based index to get hash for. Must be non-negative.
     * @return The {@link Hash} at that index, or null if there is no such {@link Hash} stored there.
     * @throws IOException
     * 		If there was a problem constructing the {@link Hash}. This should never happen.
     * @throws IndexOutOfBoundsException
     * 		if the index is less than 0, or if the index is greater than
     * 		the {@link #capacity()}.
     */
    @Override
    public Hash get(long index) throws IOException {
        final ConcurrentHashMap<Long, Hash> cache = cachedChanges.get();
        if (cache != null) {
            final Hash cachedValue = cache.get(index);
            if (cachedValue != null) {
                return cachedValue;
            }
        }
        return wrappedHashList.get(index);
    }

    /**
     * Put a {@link Hash} at the given index.
     *
     * @param index
     * 		the index at which to store the {@link Hash}. Must be non-negative.
     * @param hash
     * 		a non-null hash to write
     * @throws IndexOutOfBoundsException
     * 		if the index is less than 0, or if the index is greater than
     * 		the {@link #capacity()}.
     */
    @Override
    public void put(long index, Hash hash) {
        // Range-check on the index
        if (index < 0 || index >= maxHashes()) {
            throw new IndexOutOfBoundsException(
                    "Cannot put a hash at index " + index + " given " + maxHashes() + " capacity");
        }
        final ConcurrentHashMap<Long, Hash> cache = cachedChanges.get();
        if (cache != null) {
            cache.compute(index, (k, v) -> {
                if (skipCacheOnWrite.get() && v == null) {
                    wrappedHashList.put(index, hash);
                    return null;
                } else {
                    return hash;
                }
            });
        } else {
            wrappedHashList.put(index, hash);
        }
    }

    /**
     * Get the maximum capacity of this data structure.
     *
     * @return The maximum capacity. Will be non-negative.
     */
    @Override
    public long capacity() {
        return wrappedHashList.capacity();
    }

    /**
     * Get the number of hashes in this hash list.
     *
     * @return The size of the list. Will be non-negative.
     */
    @Override
    public long size() {
        long size;
        // get a read lock on cachedChanges state, so we know it is not changing
        final ConcurrentHashMap<Long, Hash> cache = cachedChanges.get();
        if (cache != null) {
            long maxKeyInCache = 0;
            if (!cache.isEmpty()) {
                maxKeyInCache =
                        cache.keySet().stream().mapToLong(Long::longValue).max().getAsLong();
            }
            size = Math.max(maxKeyInCache + 1, wrappedHashList.size());
        } else {
            size = wrappedHashList.size();
        }
        return size;
    }

    /**
     * Get the maximum number of hashes this HashList can store, this is the maximum value capacity can grow to.
     *
     * @return maximum number of hashes this HashList can store
     */
    @Override
    public long maxHashes() {
        return wrappedHashList.maxHashes();
    }

    /**
     * Write all hashes in this HashList into a file.<B>Important for this BufferedWrapper, only the wrapped data is
     * included in written file. This behaviour is expected and depended on by
     * {@link MerkleDbDataSource}</B>
     *
     * @param file
     * 		The file to write into, it should not exist but its parent directory should exist and be writable.
     * @throws IOException
     * 		If there was a problem creating or writing to the file.
     */
    @Override
    public void writeToFile(Path file) throws IOException {
        wrappedHashList.writeToFile(file);
    }
}
