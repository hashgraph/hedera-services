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

package com.hedera.node.app.spi.state.serdes;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/** Copied here as a test class to serve as a realistic example of a {@link VirtualKey}
 * from the Hedera codebase. */
public class VirtualBlobKey implements VirtualKey<VirtualBlobKey> {
    static final int CURRENT_VERSION = 1;
    static final int BYTES_IN_SERIALIZED_FORM = 5;
    static final long CLASS_ID = 0x11b982c14217d523L;

    private static final Type[] BLOB_TYPES = Type.values();

    public enum Type {
        FILE_DATA,
        FILE_METADATA,
        CONTRACT_BYTECODE,
        SYSTEM_DELETED_ENTITY_EXPIRY
    }

    private Type type;
    private int entityNumCode;

    public VirtualBlobKey() {
        /* Required by deserialization facility */
    }

    public VirtualBlobKey(final Type type, final int entityNumCode) {
        this.type = type;
        this.entityNumCode = entityNumCode;
    }

    @Override
    public void serialize(final ByteBuffer buffer) {
        buffer.put((byte) type.ordinal());
        buffer.putInt(entityNumCode);
    }

    @Override
    public void deserialize(final ByteBuffer buffer, final int version) {
        type = BLOB_TYPES[0xff & buffer.get()];
        entityNumCode = buffer.getInt();
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        type = BLOB_TYPES[0xff & in.readByte()];
        entityNumCode = in.readInt();
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeByte(type.ordinal());
        out.writeInt(entityNumCode);
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || VirtualBlobKey.class != o.getClass()) {
            return false;
        }

        final var that = (VirtualBlobKey) o;

        return this.type == that.type && this.entityNumCode == that.entityNumCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityNumCode, ((long) type.ordinal()) << 4);
    }

    public static int sizeInBytes() {
        return BYTES_IN_SERIALIZED_FORM;
    }

    public Type getType() {
        return type;
    }

    public int getEntityNumCode() {
        return entityNumCode;
    }

    @Override
    public int compareTo(@NonNull final VirtualBlobKey that) {
        if (this == that) {
            return 0;
        }
        final int order = Integer.compare(this.entityNumCode, that.entityNumCode);
        if (order != 0) {
            return order;
        }
        return this.type.compareTo(that.type);
    }

    @Override
    public int getMinimumSupportedVersion() {
        return CURRENT_VERSION;
    }
}
