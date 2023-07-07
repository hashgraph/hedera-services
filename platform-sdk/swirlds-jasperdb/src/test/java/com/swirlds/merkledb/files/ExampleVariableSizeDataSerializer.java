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

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
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

    @Override
    public int getSerializedSize(long[] data) {
        return Long.BYTES + (Long.BYTES * data.length); // Size + data
    }

    /** Get the current data item serialization version */
    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    @Override
    public long[] deserialize(ReadableSequentialData in) throws IOException {
        int dataSize = (int) in.readLong(); // int stored as long
        int repeats = (dataSize - Long.BYTES) / Long.BYTES;
        long[] dataItem = new long[repeats];
        // read key and data longs
        for (int i = 0; i < dataItem.length; i++) {
            dataItem[i] = in.readLong();
        }
        return dataItem;
    }

    @Override
    public void serialize(final long[] data, final WritableSequentialData out) throws IOException {
        int dataSizeBytes = Long.BYTES + (Long.BYTES * data.length); // Size + data
        out.writeLong(dataSizeBytes);
        for (long d : data) {
            out.writeLong(d);
        }
    }

    @Override
    public long deserializeKey(BufferedData dataItemData) {
        return dataItemData.getLong(Long.BYTES); // Read the second long. The first one is the size
    }
}
