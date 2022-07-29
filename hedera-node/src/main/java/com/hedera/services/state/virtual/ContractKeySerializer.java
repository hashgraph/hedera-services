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

import static com.hedera.services.state.virtual.ContractKey.deserializeContractID;
import static com.hedera.services.state.virtual.ContractKey.getContractIdNonZeroBytesFromPacked;
import static com.hedera.services.state.virtual.ContractKey.getUint256KeyNonZeroBytesFromPacked;
import static com.hedera.services.state.virtual.KeyPackingUtils.deserializeUint256Key;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/** KeySerializer for ContractKeys */
public class ContractKeySerializer implements KeySerializer<ContractKey> {
    static final long CLASS_ID = 0xfb12270526c45316L;
    static final int CURRENT_VERSION = 1;

    static final long DATA_VERSION = 1;

    /**
     * Get if the number of bytes a data item takes when serialized is variable or fixed
     *
     * @return true if getSerializedSize() == DataFileCommon.VARIABLE_DATA_SIZE
     */
    @Override
    public boolean isVariableSize() {
        return true;
    }

    /**
     * Get the number of bytes a data item takes when serialized
     *
     * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
     */
    @Override
    public int getSerializedSize() {
        return DataFileCommon.VARIABLE_DATA_SIZE;
    }

    /**
     * For variable sized data get the typical number of bytes a data item takes when serialized
     *
     * @return Either for fixed size same as getSerializedSize() or an estimated typical size for
     *     data items
     */
    @Override
    public int getTypicalSerializedSize() {
        return ContractKey.ESTIMATED_AVERAGE_SIZE;
    }

    /** Get the current data item serialization version */
    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    /**
     * Deserialize a data item from a byte buffer, that was written with given data version
     *
     * @param buffer The buffer to read from containing the data item including its header
     * @param dataVersion The serialization version the data item was written with
     * @return Deserialized data item
     */
    @Override
    public ContractKey deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        Objects.requireNonNull(buffer);
        ContractKey contractKey = new ContractKey();
        contractKey.deserialize(buffer, (int) dataVersion);
        return contractKey;
    }

    /**
     * Serialize a data item including header to the output stream returning the size of the data
     * written
     *
     * @param data The data item to serialize
     * @param outputStream Output stream to write to
     */
    @Override
    public int serialize(ContractKey data, SerializableDataOutputStream outputStream)
            throws IOException {
        Objects.requireNonNull(data);
        Objects.requireNonNull(outputStream);
        return data.serializeReturningByteWritten(outputStream);
    }

    /**
     * Deserialize key size from the given byte buffer
     *
     * @param buffer Buffer to read from
     * @return The number of bytes used to store the key, including for storing the key size if
     *     needed.
     */
    @Override
    public int deserializeKeySize(ByteBuffer buffer) {
        return ContractKey.readKeySize(buffer);
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

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        /* No-op */
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        /* No-op */
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }
}
