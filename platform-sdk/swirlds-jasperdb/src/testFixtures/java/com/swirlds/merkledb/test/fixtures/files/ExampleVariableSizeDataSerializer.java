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

package com.swirlds.merkledb.test.fixtures.files;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.virtualmap.serialize.BaseSerializer;

/**
 * Very simple DataItem that is variable size and has a long key and number of long values. Designed
 * for testing.
 *
 * <p>Stores bytes size as a long so all data is longs, makes it easier to manually read file in
 * tests
 */
public class ExampleVariableSizeDataSerializer implements BaseSerializer<long[]> {

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
    public void serialize(final long[] data, final WritableSequentialData out) {
        int dataSizeBytes = Long.BYTES + (Long.BYTES * data.length); // Size + data
        out.writeLong(dataSizeBytes);
        for (long d : data) {
            out.writeLong(d);
        }
    }

    @Override
    public long[] deserialize(ReadableSequentialData in) {
        int dataSize = (int) in.readLong(); // int stored as long
        int repeats = (dataSize - Long.BYTES) / Long.BYTES;
        long[] dataItem = new long[repeats];
        // read key and data longs
        for (int i = 0; i < dataItem.length; i++) {
            dataItem[i] = in.readLong();
        }
        return dataItem;
    }
}
