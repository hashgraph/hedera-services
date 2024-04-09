/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.test.fixtures.ExampleFixedSizeVirtualValue;
import com.swirlds.merkledb.test.fixtures.ExampleFixedSizeVirtualValueSerializer;
import com.swirlds.merkledb.test.fixtures.ExampleLongKeyFixedSize;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MerkleDbTableConfigTest {

    @BeforeAll
    public static void setup() throws Exception {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.merkledb");
    }

    @Test
    void deserializeDefaultsTest() throws IOException {
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> tableConfig =
                new MerkleDbTableConfig<>(
                        (short) 1, DigestType.SHA_384,
                        (short) 1, new ExampleLongKeyFixedSize.Serializer(),
                        (short) 1, new ExampleFixedSizeVirtualValueSerializer());

        final MerkleDbConfig dbConfig = ConfigurationHolder.getConfigData(MerkleDbConfig.class);
        Assertions.assertEquals(dbConfig.maxNumOfKeys(), tableConfig.getMaxNumberOfKeys());
        Assertions.assertEquals(dbConfig.hashesRamToDiskThreshold(), tableConfig.getHashesRamToDiskThreshold());

        Assertions.assertThrows(IllegalArgumentException.class, () -> tableConfig.maxNumberOfKeys(0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> tableConfig.maxNumberOfKeys(-1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> tableConfig.hashesRamToDiskThreshold(-1));

        // Default protobuf value, will not be serialized
        tableConfig.hashesRamToDiskThreshold(0);

        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (final WritableStreamingData out = new WritableStreamingData(bout)) {
            tableConfig.writeTo(out);
        }

        final byte[] arr = bout.toByteArray();
        final MerkleDbTableConfig<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> restored;
        try (final ReadableStreamingData in = new ReadableStreamingData(arr)) {
            restored = new MerkleDbTableConfig<>(in);
        }

        Assertions.assertEquals(dbConfig.maxNumOfKeys(), restored.getMaxNumberOfKeys());
        // Fields that aren't deserialized should have default protobuf values (e.g. zero), not
        // default MerkleDbConfig values
        Assertions.assertEquals(0, restored.getHashesRamToDiskThreshold());
    }
}
