/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual;

import static com.hedera.services.state.virtual.ContractKeySerializer.CLASS_ID;
import static com.hedera.services.state.virtual.ContractKeySerializer.CURRENT_VERSION;
import static com.hedera.services.state.virtual.ContractKeySerializer.DATA_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class ContractKeySerializerTest {
    private final long contractNum = 1234L;
    private final long otherContractNum = 1235L;
    private final long key = 123L;
    private final UInt256 largeKey =
            UInt256.fromHexString(
                    "0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563");

    final ContractKeySerializer subject = new ContractKeySerializer();
    final ContractKey contractKey = new ContractKey(contractNum, key);

    @Test
    void gettersWork() {
        assertTrue(subject.isVariableSize());
        assertEquals(DATA_VERSION, subject.getCurrentDataVersion());
        assertEquals(CLASS_ID, subject.getClassId());
        assertEquals(CURRENT_VERSION, subject.getVersion());
        assertEquals(DataFileCommon.VARIABLE_DATA_SIZE, subject.getSerializedSize());
        assertEquals(ContractKey.ESTIMATED_AVERAGE_SIZE, subject.getTypicalSerializedSize());
    }

    @Test
    void deserializerWorks() throws IOException {
        final var bin = mock(ByteBuffer.class);
        given(bin.get())
                .willReturn(contractKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
                .willReturn((byte) (contractKey.getContractId() >> 8))
                .willReturn((byte) (contractKey.getContractId()))
                .willReturn(contractKey.getUint256Byte(0));

        assertEquals(contractKey, subject.deserialize(bin, 1));
    }

    @Test
    void serializerWorks() throws IOException {
        final var contractIdNonZeroBytes = contractKey.getContractIdNonZeroBytes();
        final var uint256KeyNonZeroBytes = contractKey.getUint256KeyNonZeroBytes();

        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(contractKey, out);

        inOrder.verify(out).write(contractKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        for (int b = contractIdNonZeroBytes - 1; b >= 0; b--) {
            inOrder.verify(out).write((byte) (contractKey.getContractId() >> (b * 8)));
        }
        for (int b = uint256KeyNonZeroBytes - 1; b >= 0; b--) {
            inOrder.verify(out).write(contractKey.getUint256Byte(b));
        }
    }

    @Test
    void deserializeKeySizeWorks() {
        final var contractIdNonZeroBytes = contractKey.getContractIdNonZeroBytes();
        final var uint256KeyNonZeroBytes = contractKey.getUint256KeyNonZeroBytes();
        final var bin = mock(ByteBuffer.class);

        given(bin.get())
                .willReturn(contractKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());

        assertEquals(
                1 + contractIdNonZeroBytes + uint256KeyNonZeroBytes,
                subject.deserializeKeySize(bin));
    }

    @Test
    void equalsUsingByteBufferWorks() throws IOException {
        final var someKey = new ContractKey(0L, key);
        final var anIdenticalKey = new ContractKey(0L, key);
        final var bin = mock(ByteBuffer.class);

        given(bin.get())
                .willReturn(someKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
                .willReturn((byte) (someKey.getContractId()))
                .willReturn(someKey.getUint256Byte(0));

        assertTrue(subject.equals(bin, 1, anIdenticalKey));
    }

    @Test
    void equalsUsingByteBufferFailsAsExpected() throws IOException {
        final var someKey = new ContractKey(contractNum, key);
        final var someKeyForDiffContractButSameNonZeroBytes =
                new ContractKey(otherContractNum, key);
        final var someKeyForDiffContract = new ContractKey(Long.MAX_VALUE, key);
        final var someDiffKeyForSameContract = new ContractKey(contractNum, largeKey.toArray());
        final var bin = mock(ByteBuffer.class);

        given(bin.get())
                .willReturn(someKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
                .willReturn((byte) (someKey.getContractId() >> 8))
                .willReturn((byte) (someKey.getContractId()))
                .willReturn(someKey.getUint256Byte(0));
        assertFalse(subject.equals(bin, 1, someKeyForDiffContract));

        given(bin.get())
                .willReturn(someKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
                .willReturn((byte) (someKey.getContractId() >> 8))
                .willReturn((byte) (someKey.getContractId()))
                .willReturn(someKey.getUint256Byte(0));
        assertFalse(subject.equals(bin, 1, someKeyForDiffContractButSameNonZeroBytes));

        given(bin.get())
                .willReturn(someKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
                .willReturn((byte) (someKey.getContractId() >> 8))
                .willReturn((byte) (someKey.getContractId()))
                .willReturn(someKey.getUint256Byte(0));
        assertFalse(subject.equals(bin, 1, someDiffKeyForSameContract));
    }
}
