/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.benchmark;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataItemHeader;
import com.swirlds.jasperdb.files.DataItemSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

public class BenchmarkRecordSerializer implements DataItemSerializer<BenchmarkRecord> {

    @Override
    public int getSerializedSize() {
        return Integer.BYTES + BenchmarkRecord.getSerializedSize();
    }

    @Override
    public long getCurrentDataVersion() {
        return BenchmarkValue.VERSION;
    }

    @Override
    public BenchmarkRecord deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        int size = buffer.getInt();
        BenchmarkRecord data = new BenchmarkRecord();
        data.deserialize(buffer, (int) dataVersion);
        return data;
    }

    @Override
    public int serialize(BenchmarkRecord data, SerializableDataOutputStream outputStream) throws IOException {
        outputStream.writeInt(getSerializedSize());
        data.serialize(outputStream);
        return getSerializedSize();
    }

    @Override
    public int getHeaderSize() {
        return Integer.BYTES + Long.BYTES;
    }

    @Override
    public DataItemHeader deserializeHeader(ByteBuffer buffer) {
        int size = buffer.getInt();
        long key = buffer.getLong();
        return new DataItemHeader(size, key);
    }
}
