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

package com.swirlds.merkledb.files;

import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Very simple DataItem that is variable size and has a long key and number of long values. Designed
 * for testing.
 *
 * <p>Stores bytes size as a long so all data is longs, makes it easier to manually read file in
 * tests
 */
public class ExampleVariableSizeDataSerializer implements DataItemSerializer<long[]> {

    /**
     * Get the number of bytes used for data item header
     *
     * @return size of header in bytes
     */
    @Override
    public int getHeaderSize() {
        return Long.BYTES + Long.BYTES; // size bytes and key
    }

    /**
     * Deserialize data item header from the given byte buffer
     *
     * @param buffer Buffer to read from
     * @return The read header
     */
    @Override
    public DataItemHeader deserializeHeader(ByteBuffer buffer) {
        return new DataItemHeader((int) buffer.getLong(), buffer.getLong());
    }

    /**
     * Get the number of bytes a data item takes when serialized
     *
     * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
     */
    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getTypicalSerializedSize() {
        return Long.BYTES * 12;
    }

    /** Get the current data item serialization version */
    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    /**
     * Deserialize a data item from a byte buffer, that was written with given data version
     *
     * @param buffer The buffer to read from containing the data item including its header
     * @param dataVersion The serialization version the data item was written with
     * @return Deserialized data item
     */
    @Override
    public long[] deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        int dataSize = (int) buffer.getLong(); // int stored as long
        int repeats = (dataSize - Long.BYTES) / Long.BYTES;
        long[] dataItem = new long[repeats];
        // read key and data longs
        for (int i = 0; i < dataItem.length; i++) {
            dataItem[i] = buffer.getLong();
        }
        return dataItem;
    }

    @Override
    public int serialize(final long[] data, final ByteBuffer buffer) throws IOException {
        int dataSizeBytes = Long.BYTES + (Long.BYTES * data.length); // Size + data
        buffer.putLong(dataSizeBytes);
        for (long d : data) {
            buffer.putLong(d);
        }
        return dataSizeBytes;
    }
}
