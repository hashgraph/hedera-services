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

import com.swirlds.common.crypto.Hash;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKey;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKeyBuilder;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKeySerializer;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValue;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValueBuilder;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VirtualMerkleJPDBLeafHasherTest {

    static Path storeDir;
    static SmartContractByteCodeMapKeySerializer keySerializer;
    static VirtualLeafRecordSerializer<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> leafRecordSerializer;
    static JasperDbBuilder jasperDbBuilder;

    @BeforeAll
    static void beforeAll() {
        try {
            storeDir = Files.createTempDirectory("VirtualMerkleLeafHasherTest");
        } catch (IOException e) {
            e.printStackTrace();
        }

        keySerializer = new SmartContractByteCodeMapKeySerializer();
        leafRecordSerializer = new VirtualLeafRecordSerializer<>(
                (short) 1,
                keySerializer.getSerializedSize(),
                new SmartContractByteCodeMapKeyBuilder(),
                (short) 1,
                DataFileCommon.VARIABLE_DATA_SIZE,
                new SmartContractByteCodeMapValueBuilder(),
                true);

        jasperDbBuilder = new JasperDbBuilder()
                .virtualLeafRecordSerializer(leafRecordSerializer)
                .virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
                .keySerializer(keySerializer)
                .maxNumOfKeys(50_000_000)
                .internalHashesRamToDiskThreshold(0)
                .preferDiskBasedIndexes(false);
    }

    @Test
    void checkSimpleHashing2() throws IOException, InterruptedException {
        jasperDbBuilder.storageDir(Path.of(storeDir.toString(), "test2"));

        VirtualMap virtualMap = new VirtualMap("test2", jasperDbBuilder);

        final VirtualMerkleLeafHasher hasher = new VirtualMerkleLeafHasher(virtualMap);

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
        jasperDbBuilder.storageDir(Path.of(storeDir.toString(), "test3"));

        VirtualMap virtualMap = new VirtualMap("test3", jasperDbBuilder);

        final VirtualMerkleLeafHasher hasher = new VirtualMerkleLeafHasher(virtualMap);

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
        jasperDbBuilder.storageDir(Path.of(storeDir.toString(), "test4"));

        VirtualMap virtualMap = new VirtualMap("test4", jasperDbBuilder);

        final VirtualMerkleLeafHasher hasher = new VirtualMerkleLeafHasher(virtualMap);
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
