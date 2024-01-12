/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
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
    public int getHeaderSize() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public DataItemHeader deserializeHeader(ByteBuffer buffer) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void serialize(final BenchmarkRecord data, final WritableSequentialData out) {
        data.serialize(out);
    }

    @Override
    public void serialize(BenchmarkRecord data, ByteBuffer buffer) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public BenchmarkRecord deserialize(final ReadableSequentialData in) {
        BenchmarkRecord data = new BenchmarkRecord();
        data.deserialize(in);
        return data;
    }

    @Override
    public BenchmarkRecord deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
