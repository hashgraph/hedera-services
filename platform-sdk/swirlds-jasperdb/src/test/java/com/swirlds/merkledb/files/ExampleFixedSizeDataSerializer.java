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
 * Very simple DataItem that is fixed size and has a long key and long value. Designed for testing
 */
public class ExampleFixedSizeDataSerializer implements DataItemSerializer<long[]> {

    /**
     * Get the number of bytes a data item takes when serialized
     *
     * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
     */
    @Override
    public int getSerializedSize() {
        return Long.BYTES * 2;
    }

    /** Get the current data item serialization version */
    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    @Override
    @Deprecated(forRemoval = true)
    public int getHeaderSize() {
        return Long.BYTES;
    }

    @Override
    @Deprecated(forRemoval = true)
    public DataItemHeader deserializeHeader(ByteBuffer buffer) {
        return new DataItemHeader(Long.BYTES * 2, buffer.getLong());
    }

    @Override
    @Deprecated(forRemoval = true)
    public int serialize(final long[] data, final ByteBuffer buffer) throws IOException {
        buffer.putLong(data[0]);
        buffer.putLong(data[1]);
        return getSerializedSize();
    }

    @Override
    public void serialize(final long[] data, final WritableSequentialData out) throws IOException {
        out.writeLong(data[0]);
        out.writeLong(data[1]);
    }

    @Override
    public long[] deserialize(final ReadableSequentialData in) throws IOException {
        return new long[] {in.readLong(), in.readLong()};
    }

    @Override
    @Deprecated(forRemoval = true)
    public long[] deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        return new long[] {buffer.getLong(), buffer.getLong()};
    }

    @Override
    public long extractKey(BufferedData dataItemData) {
        return dataItemData.getLong(0);
    }
}
