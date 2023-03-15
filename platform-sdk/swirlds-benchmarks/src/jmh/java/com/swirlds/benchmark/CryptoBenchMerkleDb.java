/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.virtualmap.VirtualMap;
import org.openjdk.jmh.annotations.Setup;

public class CryptoBenchMerkleDb extends CryptoBench {

    @Setup
    public static void setupMerkleDb() throws Exception {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.merkledb");
    }

    protected VirtualMap<BenchmarkKey, BenchmarkValue> createEmptyMap() {
        MerkleDb.setDefaultPath(getTestDir().resolve("merkledb"));
        MerkleDbTableConfig<BenchmarkKey, BenchmarkValue> tableConfig = new MerkleDbTableConfig<>(
                        (short) 1, DigestType.SHA_384,
                        (short) 1, new BenchmarkKeyMerkleDbSerializer(),
                        (short) 1, new BenchmarkValueMerkleDbSerializer())
                .preferDiskIndices(false);
        MerkleDbDataSourceBuilder<BenchmarkKey, BenchmarkValue> dataSourceBuilder =
                new MerkleDbDataSourceBuilder<>(tableConfig);
        final VirtualMap<BenchmarkKey, BenchmarkValue> createdMap = new VirtualMap<>("vm" + System.nanoTime(), dataSourceBuilder);
        BenchmarkMetrics.register(createdMap::registerMetrics);
        return createdMap;
    }
}
