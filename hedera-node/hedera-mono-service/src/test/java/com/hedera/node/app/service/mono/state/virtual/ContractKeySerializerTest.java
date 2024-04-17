/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual;

import static com.hedera.node.app.service.mono.state.virtual.ContractKeySerializer.CLASS_ID;
import static com.hedera.node.app.service.mono.state.virtual.ContractKeySerializer.CURRENT_VERSION;
import static com.hedera.node.app.service.mono.state.virtual.ContractKeySerializer.DATA_VERSION;
import static com.swirlds.merkledb.serialize.BaseSerializer.VARIABLE_DATA_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class ContractKeySerializerTest {
    private final long contractNum = 1234L;
    private final long otherContractNum = 1235L;
    private final long key = 123L;
    private final UInt256 largeKey =
            UInt256.fromHexString("0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563");

    final ContractKeySerializer subject = new ContractKeySerializer();
    final ContractKey contractKey = new ContractKey(contractNum, key);

    @Test
    void gettersWork() {
        assertTrue(subject.isVariableSize());
        assertEquals(DATA_VERSION, subject.getCurrentDataVersion());
        assertEquals(CLASS_ID, subject.getClassId());
        assertEquals(CURRENT_VERSION, subject.getVersion());
        assertEquals(VARIABLE_DATA_SIZE, subject.getSerializedSize());
        assertEquals(ContractKey.ESTIMATED_AVERAGE_SIZE, subject.getTypicalSerializedSize());
    }

    @Test
    void deserializerWorks() {
        final BufferedData bin = BufferedData.allocate(4);
        bin.writeByte(contractKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        bin.writeByte((byte) (contractKey.getContractId() >> 8));
        bin.writeByte((byte) (contractKey.getContractId()));
        bin.writeByte(contractKey.getUint256Byte(0));
        bin.flip();

        assertEquals(contractKey, subject.deserialize(bin));
    }

    @Test
    void serializerWorks() {
        final var contractIdNonZeroBytes = contractKey.getContractIdNonZeroBytes();
        final var uint256KeyNonZeroBytes = contractKey.getUint256KeyNonZeroBytes();

        final BufferedData out = BufferedData.allocate(4);

        subject.serialize(contractKey, out);
        out.flip();

        final BufferedData byteBuffer = BufferedData.allocate(4);

        byteBuffer.writeByte(contractKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        for (int b = contractIdNonZeroBytes - 1; b >= 0; b--) {
            byteBuffer.writeByte((byte) (contractKey.getContractId() >> (b * 8)));
        }
        for (int b = uint256KeyNonZeroBytes - 1; b >= 0; b--) {
            byteBuffer.writeByte(contractKey.getUint256Byte(b));
        }
        byteBuffer.flip();

        assertEquals(out, byteBuffer);
    }

    @Test
    void serializedSizeWorks() {
        final var contractIdNonZeroBytes = contractKey.getContractIdNonZeroBytes();
        final var uint256KeyNonZeroBytes = contractKey.getUint256KeyNonZeroBytes();
        assertEquals(1 + contractIdNonZeroBytes + uint256KeyNonZeroBytes, subject.getSerializedSize(contractKey));
    }

    @Test
    void equalsUsingByteBufferWorks() {
        final var someKey = new ContractKey(0L, key);
        final var anIdenticalKey = new ContractKey(0L, key);
        final BufferedData bin = BufferedData.allocate(3);
        bin.writeByte(someKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        bin.writeByte((byte) someKey.getContractId());
        bin.writeByte(someKey.getUint256Byte(0));
        bin.flip();

        assertTrue(subject.equals(bin, anIdenticalKey));
    }

    @Test
    void equalsUsingByteBufferFailsAsExpected() {
        final var someKey = new ContractKey(contractNum, key);
        final var someKeyForDiffContractButSameNonZeroBytes = new ContractKey(otherContractNum, key);
        final var someKeyForDiffContract = new ContractKey(Long.MAX_VALUE, key);
        final var someDiffKeyForSameContract = new ContractKey(contractNum, largeKey.toArray());
        final BufferedData bin = BufferedData.allocate(4);

        bin.writeByte(someKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        bin.writeByte((byte) (someKey.getContractId() >> 8));
        bin.writeByte((byte) (someKey.getContractId()));
        bin.writeByte(someKey.getUint256Byte(0));
        bin.reset();
        assertFalse(subject.equals(bin, someKeyForDiffContract));

        bin.reset();
        bin.writeByte(someKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        bin.writeByte((byte) (someKey.getContractId() >> 8));
        bin.writeByte((byte) (someKey.getContractId()));
        bin.writeByte(someKey.getUint256Byte(0));
        bin.reset();
        assertFalse(subject.equals(bin, someKeyForDiffContractButSameNonZeroBytes));

        bin.reset();

        bin.writeByte(someKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        bin.writeByte((byte) (someKey.getContractId() >> 8));
        bin.writeByte((byte) (someKey.getContractId()));
        bin.writeByte(someKey.getUint256Byte(0));
        bin.reset();

        assertFalse(subject.equals(bin, someDiffKeyForSameContract));
    }
}
