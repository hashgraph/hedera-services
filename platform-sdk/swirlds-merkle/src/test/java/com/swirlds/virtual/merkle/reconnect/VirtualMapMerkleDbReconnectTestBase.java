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

package com.swirlds.virtual.merkle.reconnect;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.virtual.merkle.TestKey;
import com.swirlds.virtual.merkle.TestKeySerializerMerkleDb;
import com.swirlds.virtual.merkle.TestValue;
import com.swirlds.virtual.merkle.TestValueSerializerMerkleDb;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;

public class VirtualMapMerkleDbReconnectTestBase extends VirtualMapReconnectTestBase {

    @BeforeAll
    public static void setup() throws Exception {
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(TestKeySerializerMerkleDb.class, TestKeySerializerMerkleDb::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(TestValueSerializerMerkleDb.class, TestValueSerializerMerkleDb::new));
    }

    @Override
    protected VirtualDataSourceBuilder<TestKey, TestValue> createBuilder() throws IOException {
        // The tests create maps with identical names. They would conflict with each other in the default
        // MerkleDb instance, so let's use a new (temp) database location for every run
        final Path defaultVirtualMapPath = TemporaryFileBuilder.buildTemporaryFile();
        MerkleDb.setDefaultPath(defaultVirtualMapPath);
        final MerkleDbTableConfig<TestKey, TestValue> tableConfig = new MerkleDbTableConfig<>(
                (short) 1, DigestType.SHA_384,
                (short) 1, new TestKeySerializerMerkleDb(),
                (short) 1, new TestValueSerializerMerkleDb());
        return new MerkleDbDataSourceBuilder<>(tableConfig);
    }

    @Override
    protected BrokenBuilder createBrokenBuilder(final VirtualDataSourceBuilder<TestKey, TestValue> delegate) {
        return new BrokenBuilderMerkleDb(delegate);
    }

    @BeforeAll
    public static void setupJPDB() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(BrokenBuilderMerkleDb.class, BrokenBuilderMerkleDb::new));
    }
}
