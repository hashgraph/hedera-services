/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual.schedule;

import com.swirlds.merkledb.serialize.ValueSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ScheduleSecondVirtualValueSerializer implements ValueSerializer<ScheduleSecondVirtualValue> {
    static final long CLASS_ID = 0x218245c13df4e2c0L;
    static final int CURRENT_VERSION = 1;
    static final int DATA_VERSION = 1;

    // guesstimate of the typical size of a serialized value
    private static final int TYPICAL_SIZE = 256;

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getTypicalSerializedSize() {
        return TYPICAL_SIZE;
    }

    @Override
    public int serialize(ScheduleSecondVirtualValue value, ByteBuffer byteBuffer) throws IOException {
        return value.serializeReturningBytesWritten(byteBuffer);
    }

    @Override
    public ScheduleSecondVirtualValue deserialize(ByteBuffer byteBuffer, long dataVersion) throws IOException {
        final var value = new ScheduleSecondVirtualValue();
        value.deserialize(byteBuffer, (int) dataVersion);
        return value;
    }
}
