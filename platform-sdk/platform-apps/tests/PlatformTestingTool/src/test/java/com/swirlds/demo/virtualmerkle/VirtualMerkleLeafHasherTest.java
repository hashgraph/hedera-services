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

package com.swirlds.demo.virtualmerkle;

import static com.swirlds.demo.virtualmerkle.VirtualMerkleLeafHasher.hashOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKey;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKeySerializer;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValue;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValueSerializer;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VirtualMerkleLeafHasherTest {

    static final Configuration CONFIGURATION = ConfigurationBuilder.create()
            .withConfigDataType(MerkleDbConfig.class)
            .withConfigDataType(VirtualMapConfig.class)
            .withConfigDataType(TemporaryFileConfig.class)
            .withConfigDataType(StateCommonConfig.class)
            .build();
    static Path storeDir;
    static SmartContractByteCodeMapKeySerializer keySerializer;
    static SmartContractByteCodeMapValueSerializer valueSerializer;
    static MerkleDbDataSourceBuilder dataSourceBuilder;

    @BeforeAll
    static void beforeAll() {
        try {
            storeDir = Files.createTempDirectory("VirtualMerkleLeafHasherTest2");
            MerkleDb.setDefaultPath(storeDir);
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }

        keySerializer = new SmartContractByteCodeMapKeySerializer();
        valueSerializer = new SmartContractByteCodeMapValueSerializer();

        final MerkleDbConfig merkleDbConfig = CONFIGURATION.getConfigData(MerkleDbConfig.class);
        final MerkleDbTableConfig tableConfig = new MerkleDbTableConfig(
                        (short) 1,
                        DigestType.SHA_384,
                        merkleDbConfig.maxNumOfKeys(),
                        merkleDbConfig.hashesRamToDiskThreshold())
                .maxNumberOfKeys(50_000_000)
                .hashesRamToDiskThreshold(0)
                .preferDiskIndices(false);
        dataSourceBuilder = new MerkleDbDataSourceBuilder(tableConfig, CONFIGURATION);
    }

    @Test
    void checkSimpleHashing2() throws IOException, InterruptedException {
        VirtualMap virtualMap = new VirtualMap("test2", dataSourceBuilder, CONFIGURATION);

        final VirtualMerkleLeafHasher hasher = new VirtualMerkleLeafHasher(virtualMap);

        Long keyInput = 1L;
        byte[] valueInput = "first".getBytes();

        SmartContractByteCodeMapKey key = new SmartContractByteCodeMapKey(keyInput);
        SmartContractByteCodeMapValue value = new SmartContractByteCodeMapValue(valueInput);

        virtualMap.put(key.toBytes(), value.toBytes());

        Hash before = computeNextHash(null, key.toBytes(), value.toBytes());

        assertEquals(before, hasher.validate(), "Should have been equal");

        keyInput = 2L;
        valueInput = "second".getBytes();

        key = new SmartContractByteCodeMapKey(keyInput);
        value = new SmartContractByteCodeMapValue(valueInput);

        virtualMap.put(key.toBytes(), value.toBytes());

        // include previous hash first
        Hash after = computeNextHash(before, key.toBytes(), value.toBytes());

        assertEquals(after, hasher.validate(), "Should have been equal");

        virtualMap.release();
    }

    @Test
    void checkSimpleHashing3() throws IOException, InterruptedException {
        VirtualMap virtualMap = new VirtualMap("test3", dataSourceBuilder, CONFIGURATION);

        final VirtualMerkleLeafHasher hasher = new VirtualMerkleLeafHasher(virtualMap);

        final Long keyInput1 = 1L;
        final byte[] valueInput1 = "first".getBytes();
        SmartContractByteCodeMapKey key1 = new SmartContractByteCodeMapKey(keyInput1);
        SmartContractByteCodeMapValue value1 = new SmartContractByteCodeMapValue(valueInput1);

        virtualMap.put(key1.toBytes(), value1.toBytes());

        final Long keyInput2 = 2L;
        final byte[] valueInput2 = "second".getBytes();
        SmartContractByteCodeMapKey key2 = new SmartContractByteCodeMapKey(keyInput2);
        SmartContractByteCodeMapValue value2 = new SmartContractByteCodeMapValue(valueInput2);

        virtualMap.put(key2.toBytes(), value2.toBytes());

        final Long keyInput3 = 3L;
        final byte[] valueInput3 = "third".getBytes();
        SmartContractByteCodeMapKey key3 = new SmartContractByteCodeMapKey(keyInput3);
        SmartContractByteCodeMapValue value3 = new SmartContractByteCodeMapValue(valueInput3);

        virtualMap.put(key3.toBytes(), value3.toBytes());

        // include previous hash first
        Hash hash = null;

        hash = computeNextHash(hash, key2.toBytes(), value2.toBytes());
        hash = computeNextHash(hash, key1.toBytes(), value1.toBytes());
        hash = computeNextHash(hash, key3.toBytes(), value3.toBytes());

        assertEquals(hash, hasher.validate(), "Should have been equal");

        virtualMap.release();
    }

    @Test
    void checkSimpleHashing4() throws IOException, InterruptedException {
        VirtualMap virtualMap = new VirtualMap("test4", dataSourceBuilder, CONFIGURATION);

        final VirtualMerkleLeafHasher hasher = new VirtualMerkleLeafHasher(virtualMap);

        final Long keyInput1 = 1L;
        final byte[] valueInput1 = "first".getBytes();
        SmartContractByteCodeMapKey key1 = new SmartContractByteCodeMapKey(keyInput1);
        SmartContractByteCodeMapValue value1 = new SmartContractByteCodeMapValue(valueInput1);

        virtualMap.put(key1.toBytes(), value1.toBytes());

        final Long keyInput2 = 2L;
        final byte[] valueInput2 = "second".getBytes();
        SmartContractByteCodeMapKey key2 = new SmartContractByteCodeMapKey(keyInput2);
        SmartContractByteCodeMapValue value2 = new SmartContractByteCodeMapValue(valueInput2);

        virtualMap.put(key2.toBytes(), value2.toBytes());

        final Long keyInput3 = 3L;
        final byte[] valueInput3 = "third".getBytes();
        SmartContractByteCodeMapKey key3 = new SmartContractByteCodeMapKey(keyInput3);
        SmartContractByteCodeMapValue value3 = new SmartContractByteCodeMapValue(valueInput3);

        virtualMap.put(key3.toBytes(), value3.toBytes());

        final Long keyInput4 = 4L;
        final byte[] valueInput4 = "fourth".getBytes();
        SmartContractByteCodeMapKey key4 = new SmartContractByteCodeMapKey(keyInput4);
        SmartContractByteCodeMapValue value4 = new SmartContractByteCodeMapValue(valueInput4);

        virtualMap.put(key4.toBytes(), value4.toBytes());

        // include previous hash first
        Hash hash = null;

        // this is the order for the leafs from first to last
        hash = computeNextHash(hash, key1.toBytes(), value1.toBytes());
        hash = computeNextHash(hash, key3.toBytes(), value3.toBytes());
        hash = computeNextHash(hash, key2.toBytes(), value2.toBytes());
        hash = computeNextHash(hash, key4.toBytes(), value4.toBytes());

        assertEquals(hash, hasher.validate(), "Should have been equal");

        virtualMap.release();
    }

    private Hash computeNextHash(final Hash hash, final Long keyInput, final byte[] valueInput) throws IOException {
        final ByteBuffer bb = ByteBuffer.allocate(10000);

        if (hash != null) {
            hash.getBytes().writeTo(bb);
        }

        // key serializaion
        bb.putLong(keyInput);

        // value serialization
        bb.putInt(valueInput.length);
        bb.put(valueInput);

        return hashOf(Arrays.copyOf(bb.array(), bb.position()));
    }

    private Hash computeNextHash(final Hash hash, final Bytes key, final Bytes value) throws IOException {
        final ByteBuffer bb = ByteBuffer.allocate(10000);

        if (hash != null) {
            hash.getBytes().writeTo(bb);
        }

        // key serializaion
        key.writeTo(bb);

        // value serialization
        if (value != null) {
            value.writeTo(bb);
        }

        return hashOf(Arrays.copyOf(bb.array(), bb.position()));
    }
}
