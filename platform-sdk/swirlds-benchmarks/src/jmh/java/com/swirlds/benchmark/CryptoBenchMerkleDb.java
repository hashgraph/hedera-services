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
import org.openjdk.jmh.annotations.Setup;

public class CryptoBenchMerkleDb extends CryptoBench {

    @Setup
    public static void setupMerkleDb() throws Exception {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.merkledb");
    }

    @Override
    public void beforeTest(String name) {
        super.beforeTest(name);
        updateMerkleDbPath();
    }

    public static void main(String[] args) throws Exception {
        CryptoBenchMerkleDb.setupMerkleDb();
        final CryptoBenchMerkleDb bench = new CryptoBenchMerkleDb();
        bench.setup();
        bench.beforeTest();
        bench.transferPrefetch();
        bench.afterTest();
        bench.destroy();
    }
}
