/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.map.test.benchmark;

import com.swirlds.common.test.benchmark.BenchmarkMetadata;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Used to run benchmarks against the MerkleMap.
 */
public class MerkleMapBenchmarkMetadata implements BenchmarkMetadata {

    private final RandomKeySet<Long> keys;
    private long nextKey;

    private final long networkFeeKey;
    private final List<Long> nodeFeeKeys;

    private final Set<Long> undeletableKeys;

    private static final int NUMBER_OF_NODES = 13;

    public MerkleMapBenchmarkMetadata() {
        keys = new RandomKeySet<>();

        networkFeeKey = nextKey++;
        nodeFeeKeys = new ArrayList<>(NUMBER_OF_NODES);
        for (int i = 0; i < NUMBER_OF_NODES; i++) {
            nodeFeeKeys.add(nextKey++);
        }

        undeletableKeys = new HashSet<>();
    }

    /**
     * Get a new key. It is expected that this key will be inserted into the map.
     */
    public synchronized BenchmarkKey getNewKey() {
        final long key = nextKey++;
        keys.add(key);
        return new BenchmarkKey(key);
    }

    /**
     * Get a random key.
     */
    public synchronized BenchmarkKey getRandomKey(final Random random) {
        return new BenchmarkKey(keys.getRandomKey(random));
    }

    /**
     * Delete a given key.
     */
    public synchronized void deleteKey(final BenchmarkKey key) {
        keys.remove(key.getValue());
    }

    /**
     * Get the key to which the network fee is paid.
     */
    public BenchmarkKey getNetworkFeeKey() {
        return new BenchmarkKey(networkFeeKey);
    }

    /**
     * Get a random node for a node fee.
     */
    public BenchmarkKey getRandomNodeFeeKey(final Random random) {
        return new BenchmarkKey(nodeFeeKeys.get(random.nextInt(nodeFeeKeys.size())));
    }

    /**
     * Get a list of keys for all nodes that collect fees.
     */
    public List<BenchmarkKey> getNodeFeeKeys() {
        final List<BenchmarkKey> keys = new LinkedList<>();
        for (final long key : nodeFeeKeys) {
            keys.add(new BenchmarkKey(key));
        }
        return keys;
    }

    /**
     * Mark a key as undeletable.
     */
    public void markKeyAsUndeletable(final BenchmarkKey key) {
        undeletableKeys.add(key.getValue());
    }

    /**
     * Remove the undeletable status from a key.
     */
    public void markKeyAsDeletable(final BenchmarkKey key) {
        undeletableKeys.remove(key.getValue());
    }

    /**
     * Check if a key is undeletable.
     */
    public boolean isKeyUndeletable(final BenchmarkKey key) {
        return undeletableKeys.contains(key.getValue());
    }
}
