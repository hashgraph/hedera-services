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

package com.swirlds.benchmark;

import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.files.DataFileCompactor;
import com.swirlds.merkledb.files.hashmap.HalfDiskHashMap;
import java.util.Arrays;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
public class HalfDiskMapBench extends BaseBench {

    private static final long INVALID_PATH = -1L;

    String benchmarkName() {
        return "KeyValueStoreBench";
    }

    @Benchmark
    public void merge() throws Exception {
        String storeName = "mergeBench";
        beforeTest(storeName);

        final long[] map = new long[verify ? maxKey : 0];
        Arrays.fill(map, INVALID_PATH);

        final var store = new HalfDiskHashMap(configuration, maxKey, getTestDir(), storeName, null, false);
        final var dataFileCompactor = new DataFileCompactor(
                getConfig(MerkleDbConfig.class),
                storeName,
                store.getFileCollection(),
                store.getBucketIndexToBucketLocation(),
                null,
                null,
                null,
                null);
        System.out.println();

        // Write files
        final BenchmarkKeySerializer keySerializer = new BenchmarkKeySerializer();
        long start = System.currentTimeMillis();
        for (int i = 0; i < numFiles; i++) {
            store.startWriting();
            resetKeys();
            for (int j = 0; j < numRecords; ++j) {
                long id = nextAscKey();
                BenchmarkKey key = new BenchmarkKey(id);
                long value = nextValue();
                store.put(keySerializer.toBytes(key), key.hashCode(), value);
                if (verify) map[(int) id] = value;
            }
            store.endWriting();
        }
        System.out.println("Created " + numFiles + " files in " + (System.currentTimeMillis() - start) + "ms");

        // Merge files
        start = System.currentTimeMillis();
        dataFileCompactor.compact();
        System.out.println("Compacted files in " + (System.currentTimeMillis() - start) + "ms");

        // Verify merged content
        if (verify) {
            start = System.currentTimeMillis();
            for (int id = 0; id < map.length; ++id) {
                final BenchmarkKey key = new BenchmarkKey(id);
                long value = store.get(keySerializer.toBytes(key), key.hashCode(), INVALID_PATH);
                if (value != map[id]) {
                    throw new RuntimeException("Bad value");
                }
            }
            System.out.println("Verified HalfDiskHashMap in " + (System.currentTimeMillis() - start) + "ms");
        }

        afterTest(store::close);
    }
}
