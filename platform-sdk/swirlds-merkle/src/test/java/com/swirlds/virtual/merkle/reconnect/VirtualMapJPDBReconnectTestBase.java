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

package com.swirlds.virtual.merkle.reconnect;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtual.merkle.TestKey;
import com.swirlds.virtual.merkle.TestKeySerializer;
import com.swirlds.virtual.merkle.TestValue;
import com.swirlds.virtual.merkle.TestValueSerializer;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import org.junit.jupiter.api.BeforeAll;

public class VirtualMapJPDBReconnectTestBase extends VirtualMapReconnectTestBase {

    @BeforeAll
    public static void setupJPDB() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(BrokenBuilderJPDB.class, BrokenBuilderJPDB::new));
    }

    @Override
    protected VirtualDataSourceBuilder<TestKey, TestValue> createBuilder() {
        return new JasperDbBuilder<TestKey, TestValue>()
                .keySerializer(new TestKeySerializer())
                .virtualLeafRecordSerializer(new VirtualLeafRecordSerializer<>(
                        (short) 1,
                        DigestType.SHA_384,
                        (short) 1,
                        TestKey.BYTES,
                        new TestKeySerializer(),
                        (short) 1,
                        DataFileCommon.VARIABLE_DATA_SIZE,
                        new TestValueSerializer(),
                        true));
    }

    @Override
    protected BrokenBuilder createBrokenBuilder(final VirtualDataSourceBuilder<TestKey, TestValue> delegate) {
        return new BrokenBuilderJPDB(delegate);
    }
}
