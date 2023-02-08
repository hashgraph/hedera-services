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
package com.hedera.node.app.service.mono.state.virtual;

import static com.hedera.node.app.service.mono.state.virtual.ContractKey.deserializeContractID;
import static com.hedera.node.app.service.mono.state.virtual.ContractKey.getContractIdNonZeroBytesFromPacked;
import static com.hedera.node.app.service.mono.state.virtual.ContractKey.getUint256KeyNonZeroBytesFromPacked;
import static com.hedera.node.app.service.mono.state.virtual.KeyPackingUtils.deserializeUint256Key;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.KeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/** KeySerializer for ContractKeys */
public class ContractMerkleDbKeySerializer implements KeySerializer<ContractKey> {

    static final long CLASS_ID = 0xfb12270526c45317L;

    static final int CURRENT_VERSION = 1;

    static final long DATA_VERSION = 1;

    // Serializer info

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    // Data version

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    // Key serialization

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getTypicalSerializedSize() {
        return ContractKey.ESTIMATED_AVERAGE_SIZE;
    }

    @Override
    public int serialize(final ContractKey key, final ByteBuffer buffer) throws IOException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(buffer);
        return key.serializeReturningBytesWritten(buffer);
    }

    @Override
    public int serialize(final ContractKey key, SerializableDataOutputStream out)
            throws IOException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(out);
        return key.serializeReturningBytesWritten(out);
    }

    // Key deserialization

    @Override
    public int deserializeKeySize(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        return ContractKey.readKeySize(buffer);
    }

    @Override
    public ContractKey deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        Objects.requireNonNull(buffer);
        ContractKey key = new ContractKey();
        key.deserialize(buffer, (int) dataVersion);
        return key;
    }

    @Override
    public boolean equals(ByteBuffer buf, int version, ContractKey contractKey) throws IOException {
        byte packedSize = buf.get();
        final byte contractIdNonZeroBytes = getContractIdNonZeroBytesFromPacked(packedSize);
        if (contractIdNonZeroBytes != contractKey.getContractIdNonZeroBytes()) return false;
        final byte uint256KeyNonZeroBytes = getUint256KeyNonZeroBytesFromPacked(packedSize);
        if (uint256KeyNonZeroBytes != contractKey.getUint256KeyNonZeroBytes()) return false;
        final long contractId = deserializeContractID(contractIdNonZeroBytes, buf, ByteBuffer::get);
        if (contractId != contractKey.getContractId()) return false;
        final int[] uint256Key =
                deserializeUint256Key(uint256KeyNonZeroBytes, buf, ByteBuffer::get);
        return Arrays.equals(uint256Key, contractKey.getKey());
    }
}
