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

package com.swirlds.demo.virtualmerkle;

import static com.swirlds.demo.virtualmerkle.VirtualMerkleLeafHasher.hashOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKey;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKeySerializerMerkleDb;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValue;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValueSerializerMerkleDb;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.virtualmap.VirtualMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VirtualMerkleMerkleDbLeafHasherTest {

    static Path storeDir;
    static SmartContractByteCodeMapKeySerializerMerkleDb keySerializer;
    static SmartContractByteCodeMapValueSerializerMerkleDb valueSerializer;
    static MerkleDbDataSourceBuilder<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> dataSourceBuilder;

    @BeforeAll
    static void beforeAll() {
        try {
            storeDir = Files.createTempDirectory("VirtualMerkleLeafHasherTest2");
            MerkleDb.setDefaultPath(storeDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        keySerializer = new SmartContractByteCodeMapKeySerializerMerkleDb();
        valueSerializer = new SmartContractByteCodeMapValueSerializerMerkleDb();
        final MerkleDbTableConfig<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> tableConfig =
                new MerkleDbTableConfig<>(
                                (short) 1, DigestType.SHA_384,
                                (short) 1, keySerializer,
                                (short) 1, valueSerializer)
                        .maxNumberOfKeys(50_000_000)
                        .internalHashesRamToDiskThreshold(0)
                        .preferDiskIndices(false);
        dataSourceBuilder = new MerkleDbDataSourceBuilder<>(tableConfig);
    }

    @Test
    void checkSimpleHashing2() throws IOException, InterruptedException {
        VirtualMap<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> virtualMap =
                new VirtualMap<>("test2", dataSourceBuilder);

        final VirtualMerkleLeafHasher<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> hasher =
                new VirtualMerkleLeafHasher<>(virtualMap);

        Long keyInput = 1L;
        byte[] valueInput = "first".getBytes();

        SmartContractByteCodeMapKey key = new SmartContractByteCodeMapKey(keyInput);
        SmartContractByteCodeMapValue value = new SmartContractByteCodeMapValue(valueInput);

        virtualMap.put(key, value);

        Hash before = computeNextHash(null, keyInput, valueInput);

        assertEquals(before, hasher.validate(), "Should have been equal");

        keyInput = 2L;
        valueInput = "second".getBytes();

        key = new SmartContractByteCodeMapKey(keyInput);
        value = new SmartContractByteCodeMapValue(valueInput);

        virtualMap.put(key, value);

        // include previous hash first
        Hash after = computeNextHash(before, keyInput, valueInput);

        assertEquals(after, hasher.validate(), "Should have been equal");

        virtualMap.release();
    }

    @Test
    void checkSimpleHashing3() throws IOException, InterruptedException {
        VirtualMap<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> virtualMap =
                new VirtualMap<>("test3", dataSourceBuilder);

        final VirtualMerkleLeafHasher<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> hasher =
                new VirtualMerkleLeafHasher<>(virtualMap);

        final Long keyInput1 = 1L;
        final byte[] valueInput1 = "first".getBytes();
        SmartContractByteCodeMapKey key = new SmartContractByteCodeMapKey(keyInput1);
        SmartContractByteCodeMapValue value = new SmartContractByteCodeMapValue(valueInput1);

        virtualMap.put(key, value);

        final Long keyInput2 = 2L;
        final byte[] valueInput2 = "second".getBytes();
        key = new SmartContractByteCodeMapKey(keyInput2);
        value = new SmartContractByteCodeMapValue(valueInput2);

        virtualMap.put(key, value);

        final Long keyInput3 = 3L;
        final byte[] valueInput3 = "third".getBytes();
        key = new SmartContractByteCodeMapKey(keyInput3);
        value = new SmartContractByteCodeMapValue(valueInput3);

        virtualMap.put(key, value);

        // include previous hash first
        Hash hash = null;

        hash = computeNextHash(hash, keyInput2, valueInput2);
        hash = computeNextHash(hash, keyInput1, valueInput1);
        hash = computeNextHash(hash, keyInput3, valueInput3);

        assertEquals(hash, hasher.validate(), "Should have been equal");

        virtualMap.release();
    }

    @Test
    void checkSimpleHashing4() throws IOException, InterruptedException {
        VirtualMap<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> virtualMap =
                new VirtualMap<>("test4", dataSourceBuilder);

        final VirtualMerkleLeafHasher<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> hasher =
                new VirtualMerkleLeafHasher<>(virtualMap);

        final Long keyInput1 = 1L;
        final byte[] valueInput1 = "first".getBytes();
        SmartContractByteCodeMapKey key = new SmartContractByteCodeMapKey(keyInput1);
        SmartContractByteCodeMapValue value = new SmartContractByteCodeMapValue(valueInput1);

        virtualMap.put(key, value);

        final Long keyInput2 = 2L;
        final byte[] valueInput2 = "second".getBytes();
        key = new SmartContractByteCodeMapKey(keyInput2);
        value = new SmartContractByteCodeMapValue(valueInput2);

        virtualMap.put(key, value);

        final Long keyInput3 = 3L;
        final byte[] valueInput3 = "third".getBytes();
        key = new SmartContractByteCodeMapKey(keyInput3);
        value = new SmartContractByteCodeMapValue(valueInput3);

        virtualMap.put(key, value);

        final Long keyInput4 = 4L;
        final byte[] valueInput4 = "fourth".getBytes();
        key = new SmartContractByteCodeMapKey(keyInput4);
        value = new SmartContractByteCodeMapValue(valueInput4);

        virtualMap.put(key, value);

        // include previous hash first
        Hash hash = null;

        // this is the order for the leafs from first to last
        hash = computeNextHash(hash, keyInput1, valueInput1);
        hash = computeNextHash(hash, keyInput3, valueInput3);
        hash = computeNextHash(hash, keyInput2, valueInput2);
        hash = computeNextHash(hash, keyInput4, valueInput4);

        assertEquals(hash, hasher.validate(), "Should have been equal");

        virtualMap.release();
    }

    private Hash computeNextHash(final Hash hash, final Long keyInput, final byte[] valueInput) throws IOException {
        final ByteBuffer bb = ByteBuffer.allocate(10000);

        if (hash != null) {
            bb.put(hash.getValue());
        }

        // key serializaion
        bb.putLong(keyInput);

        // value serialization
        bb.putInt(valueInput.length);
        bb.put(valueInput);

        return hashOf(Arrays.copyOf(bb.array(), bb.position()));
    }
}
