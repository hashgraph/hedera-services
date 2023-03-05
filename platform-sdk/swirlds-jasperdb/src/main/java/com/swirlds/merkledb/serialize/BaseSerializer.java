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

package com.swirlds.merkledb.serialize;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public interface BaseSerializer<T> {

    /**
     * Data size constant used when the data size is variable
     */
    int VARIABLE_DATA_SIZE = -1;

    /**
     * Get the current data item serialization version
     */
    long getCurrentDataVersion();

    /**
     * Get if the number of bytes a data item takes when serialized is variable or fixed
     *
     * @return true if getSerializedSize() == DataFileCommon.VARIABLE_DATA_SIZE
     */
    default boolean isVariableSize() {
        return getSerializedSize() == VARIABLE_DATA_SIZE;
    }

    /**
     * Get the number of bytes a data item takes when serialized
     *
     * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
     */
    int getSerializedSize();

    /**
     * Serialize a data item including header to the output stream returning the size of the data written.
     * Serialization format must be identical to {@link #deserialize(ByteBuffer, long)}.
     *
     * @param data
     * 		The data item to serialize
     * @param outputStream
     * 		Output stream to write to
     * @return Number of bytes written
     */
    int serialize(T data, SerializableDataOutputStream outputStream) throws IOException;

    /**
     * Deserialize a data item from a byte buffer, that was written with given data version.
     *
     * @param buffer
     * 		The buffer to read from containing the data item including its header
     * @param dataVersion
     * 		The serialization version the data item was written with
     * @return Deserialized data item
     */
    T deserialize(ByteBuffer buffer, long dataVersion) throws IOException;
}
