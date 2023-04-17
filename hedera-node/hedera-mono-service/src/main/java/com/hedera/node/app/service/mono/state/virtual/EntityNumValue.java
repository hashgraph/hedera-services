/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual;

import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.ByteBuffer;

public class EntityNumValue implements VirtualValue {
    public static final int CURRENT_VERSION = 1;
    public static final long RUNTIME_CONSTRUCTABLE_ID = 0x2e5eb64ad7cbcfeaL;
    public static final String CANNOT_DESERIALIZE_INTO_AN_IMMUTABLE_ENTITY_NUM_VALUE =
            "Cannot deserialize into an immutable EntityNumValue";

    public static final EntityNumValue DEFAULT = new EntityNumValue(0);

    private long num;

    private boolean isImmutable = false;

    public EntityNumValue() {
        // RuntimeConstructable
    }

    public EntityNumValue(final long num) {
        this.num = num;
    }

    public long num() {
        return num;
    }

    @Override
    public EntityNumValue copy() {
        return new EntityNumValue(num);
    }

    @Override
    public EntityNumValue asReadOnly() {
        final var immutableNum = copy();
        immutableNum.isImmutable = true;
        return immutableNum;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer) throws IOException {
        byteBuffer.putLong(num);
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int version) throws IOException {
        if (isImmutable) {
            throw new MutabilityException(CANNOT_DESERIALIZE_INTO_AN_IMMUTABLE_ENTITY_NUM_VALUE);
        }
        num = byteBuffer.getLong();
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        if (isImmutable) {
            throw new MutabilityException(CANNOT_DESERIALIZE_INTO_AN_IMMUTABLE_ENTITY_NUM_VALUE);
        }
        num = in.readLong();
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(num);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }
}
