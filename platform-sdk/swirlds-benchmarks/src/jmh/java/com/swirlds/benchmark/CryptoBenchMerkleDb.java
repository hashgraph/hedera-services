/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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
import java.nio.file.Path;
import org.openjdk.jmh.annotations.Setup;

public class CryptoBenchMerkleDb extends CryptoBench {

    @Setup
    public static void setupMerkleDb() throws Exception {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.merkledb");
    }

    private int dbIndex = 0;

    @Override
    public void beforeTest(String name) {
        super.beforeTest(name);
        // Use a different MerkleDb instance for every test run. With a single instance,
        // even if its folder is deleted before each run, there could be background
        // threads (virtual pipeline thread, data source compaction thread, etc.) from
        // the previous run that re-create the folder, and it results in a total mess
        final Path merkleDbPath = getTestDir().resolve("merkledb" + dbIndex++);
        MerkleDb.setDefaultPath(merkleDbPath);
    }

    protected VirtualMap<BenchmarkKey, BenchmarkValue> createEmptyMap() {
        MerkleDbTableConfig<BenchmarkKey, BenchmarkValue> tableConfig = new MerkleDbTableConfig<>(
                        (short) 1, DigestType.SHA_384,
                        (short) 1, new BenchmarkKeySerializer(),
                        (short) 1, new BenchmarkValueSerializer())
                .preferDiskIndices(false);
        MerkleDbDataSourceBuilder<BenchmarkKey, BenchmarkValue> dataSourceBuilder =
                new MerkleDbDataSourceBuilder<>(tableConfig);
        return new VirtualMap<>(LABEL, dataSourceBuilder);
    }
}
