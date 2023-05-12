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

package com.swirlds.jasperdb.files.hashmap;

import static com.swirlds.jasperdb.files.DataFileCommon.deleteDirectoryAndContents;

import com.swirlds.common.bloom.BloomFilter;
import com.swirlds.common.bloom.hasher.SelfSerializableBloomHasher;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.datasource.VirtualKeySet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * A set-like data structure that uses {@link HalfDiskHashMap} to maintain state on disk. This data structure
 * supports additions but not removals. It uses an in-memory bloom filter to reduce the required number of
 * disk reads.
 *
 * @param <K>
 * 		the type of the key
 */
public class HalfDiskVirtualKeySet<K extends VirtualKey> implements VirtualKeySet<K> {

    private static final String STORE_PREFIX = "reconnectKeySet";

    private final Set<K> unflushedData;
    private long unflushedCount = 0;

    private final BloomFilter<K> bloomFilter;

    // FUTURE WORK: this data structure is mildly inefficient for this use case, as we just need a set and not a map
    // that writes keys and values to the disk. If this component ever becomes a bottleneck, consider writing a use case
    // specific data structure.
    private final HalfDiskHashMap<K> flushedData;

    private final int maxUnflushedElements;

    private final Path tempDir;

    /**
     * Create a new key set that is built on top of a half disk hash map.
     *
     * @param keySerializer
     * 		a method that serializes keys
     * @param bloomFilterHashCount
     * 		the number of hashes to include in the bloom filter
     * @param bloomFilterSize
     * 		the size of the bloom filter, in bits
     * @param halfDiskHashMapSize
     * 		the size of the half disk hash map
     * @param maxUnflushedElements
     * 		the maximum number of elements to keep in memory before flushing to the half disk hash map
     */
    public HalfDiskVirtualKeySet(
            final KeySerializer<K> keySerializer,
            final int bloomFilterHashCount,
            final long bloomFilterSize,
            final long halfDiskHashMapSize,
            final int maxUnflushedElements) {

        this.maxUnflushedElements = maxUnflushedElements;

        unflushedData = new HashSet<>();

        bloomFilter = new BloomFilter<>(bloomFilterHashCount, new SelfSerializableBloomHasher<>(), bloomFilterSize);

        try {
            tempDir = Files.createTempDirectory(STORE_PREFIX).resolve("data");
            flushedData = new HalfDiskHashMap<>(
                    halfDiskHashMapSize,
                    keySerializer,
                    tempDir,
                    "halfdiskvirtualkeyset",
                    "halfDiskVirtualKeySet",
                    false);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(final K key) {
        unflushedData.add(key);
        unflushedCount++;

        bloomFilter.add(key);

        if (unflushedCount > maxUnflushedElements) {
            flush();
        }
    }

    private void flush() {
        flushedData.startWriting();
        unflushedData.forEach(k -> flushedData.put(k, 0));
        unflushedData.clear();
        try {
            flushedData.endWriting();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        unflushedCount = 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(final K key) {
        // This test may produce false negatives.
        if (unflushedData.contains(key)) {
            return true;
        }

        // This test may produce false positives.
        if (!bloomFilter.contains(key)) {
            return false;
        }

        // This test always returns the ground truth. But it's expensive, so hopefully we don't get here often.
        try {
            return flushedData.get(key, -1) != -1;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        try {
            flushedData.close();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        deleteDirectoryAndContents(tempDir);
    }
}
