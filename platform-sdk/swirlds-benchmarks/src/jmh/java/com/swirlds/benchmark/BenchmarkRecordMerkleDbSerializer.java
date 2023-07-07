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

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

public class BenchmarkRecordMerkleDbSerializer implements DataItemSerializer<BenchmarkRecord> {

    @Override
    public int getSerializedSize() {
        return Integer.BYTES + BenchmarkRecord.getSerializedSize();
    }

    @Override
    public long getCurrentDataVersion() {
        return BenchmarkValue.VERSION;
    }

    @Override
    public BenchmarkRecord deserialize(final ReadableSequentialData in) throws IOException {
        BenchmarkRecord data = new BenchmarkRecord();
        data.deserialize(in);
        return data;
    }

    @Override
    public long deserializeKey(BufferedData dataItemData) {
        return dataItemData.getLong(0);
    }

    @Override
    public void serialize(BenchmarkRecord data, WritableSequentialData out) throws IOException {
        out.writeInt(getSerializedSize());
        data.serialize(out);
    }
}
