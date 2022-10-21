/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle.internals;

import com.google.common.base.MoreObjects;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class BytesElement implements FastCopyable, SerializableHashable {
    private static final int CURRENT_VERSION = 1;
    private static final long CLASS_ID = 0xd1b1fc6b87447a02L;

    private Hash hash;
    private byte[] data;

    public BytesElement() {
        /* RuntimeConstructable */
    }

    public byte[] getData() {
        return data;
    }

    public BytesElement(byte[] data) {
        this.data = data;
    }

    @Override
    public BytesElement copy() {
        return this;
    }

    @Override
    public Hash getHash() {
        return hash;
    }

    @Override
    public void setHash(Hash hash) {
        this.hash = hash;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        data = in.readByteArray(Integer.MAX_VALUE);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeByteArray(data);
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || BytesElement.class != obj.getClass()) {
            return false;
        }

        var that = (BytesElement) obj;
        return Arrays.equals(this.data, that.data);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(BytesElement.class)
                .add("data", Arrays.toString(this.data))
                .toString();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.data);
    }
}
