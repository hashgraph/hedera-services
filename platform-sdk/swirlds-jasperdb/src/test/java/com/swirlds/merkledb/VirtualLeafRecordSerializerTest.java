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

package com.swirlds.merkledb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.merkledb.files.VirtualLeafRecordSerializer;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings("unchecked")
class VirtualLeafRecordSerializerTest {

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.merkledb");
    }

    @ParameterizedTest
    @EnumSource(TestType.class)
    @SuppressWarnings("rawtypes")
    void testSerializeDeserialize(final TestType testType) throws IOException {
        final KeySerializer<?> keySerializer = testType.dataType().getKeySerializer();
        final ValueSerializer<?> valueSerializer = testType.dataType().getValueSerializer();

        final MerkleDbTableConfig<VirtualLongKey, ExampleFixedSizeVirtualValue> tableConfig1 = new MerkleDbTableConfig(
                (short) 1,
                DigestType.SHA_384,
                (short) keySerializer.getCurrentDataVersion(),
                keySerializer,
                (short) valueSerializer.getCurrentDataVersion(),
                valueSerializer);
        final VirtualLeafRecordSerializer<VirtualLongKey, ExampleFixedSizeVirtualValue> virtualLeafRecordSerializer1 =
                new VirtualLeafRecordSerializer<>(tableConfig1);

        final MerkleDbTableConfig<VirtualLongKey, ExampleFixedSizeVirtualValue> tableConfig2 = new MerkleDbTableConfig(
                (short) 1,
                DigestType.SHA_384,
                (short) keySerializer.getCurrentDataVersion(),
                keySerializer,
                (short) valueSerializer.getCurrentDataVersion(),
                valueSerializer);
        final VirtualLeafRecordSerializer<VirtualLongKey, ExampleFixedSizeVirtualValue> virtualLeafRecordSerializer2 =
                new VirtualLeafRecordSerializer<>(tableConfig1);

        assertEquals(
                virtualLeafRecordSerializer1,
                virtualLeafRecordSerializer2,
                "Two identical VirtualLeafRecordSerializers did not equal each other");
    }
}
