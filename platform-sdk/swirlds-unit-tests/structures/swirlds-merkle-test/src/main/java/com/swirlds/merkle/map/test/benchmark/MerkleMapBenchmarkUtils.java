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

import static com.swirlds.common.test.RandomUtils.randomByteArray;

import com.swirlds.merkle.map.MerkleMap;
import java.util.Random;

public final class MerkleMapBenchmarkUtils {

    private MerkleMapBenchmarkUtils() {}

    /**
     * Pre-populate a map with a bunch of accounts.
     *
     * @param random
     * 		a source of randomness
     * @param dataSize
     * 		the size of the auxiliary data for the account
     * @param benchmarkMetadata
     * 		metadata for the benchmark
     * @param size
     * 		the number of accounts to create
     * @param accountFactory
     * 		an object which can create accounts of the proper type
     */
    public static <A extends BenchmarkAccount> MerkleMap<BenchmarkKey, A> generateInitialState(
            final Random random,
            final int dataSize,
            final MerkleMapBenchmarkMetadata benchmarkMetadata,
            final int size,
            AccountFactory<A> accountFactory) {

        final MerkleMap<BenchmarkKey, A> map = new MerkleMap<>();

        // Create account for network fees
        map.put(
                benchmarkMetadata.getNetworkFeeKey(),
                accountFactory.buildAccount(random.nextLong(), randomByteArray(random, dataSize)));

        // Create accounts for node fees
        for (final BenchmarkKey key : benchmarkMetadata.getNodeFeeKeys()) {
            map.put(key, accountFactory.buildAccount(random.nextLong(), randomByteArray(random, dataSize)));
        }

        // Create a bunch of user accounts
        for (int i = 0; i < size; i++) {
            final BenchmarkKey key = benchmarkMetadata.getNewKey();
            map.put(key, accountFactory.buildAccount(random.nextLong(), randomByteArray(random, dataSize)));
        }

        return map;
    }
}
