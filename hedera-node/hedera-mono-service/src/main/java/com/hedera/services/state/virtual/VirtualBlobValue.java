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
package com.hedera.services.state.virtual;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class VirtualBlobValue implements VirtualValue {
    static final int CURRENT_VERSION = 1;
    static final long CLASS_ID = 0x7eb72381159d8402L;

    private byte[] data;

    public VirtualBlobValue() {
        /* Required by deserialization facility */
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public VirtualBlobValue(byte[] data) {
        this.data = data;
    }

    public VirtualBlobValue(final VirtualBlobValue that) {
        this.data = that.data;
    }

    @Override
    public VirtualBlobValue copy() {
        return new VirtualBlobValue(this);
    }

    @Override
    public VirtualValue asReadOnly() {
        return copy();
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
    public void serialize(ByteBuffer buffer) throws IOException {
        buffer.putInt(data.length);
        buffer.put(data);
    }

    @Override
    public void deserialize(ByteBuffer buffer, int version) throws IOException {
        final var n = buffer.getInt();
        data = new byte[n];
        buffer.get(data);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    public static int sizeInBytes() {
        return DataFileCommon.VARIABLE_DATA_SIZE;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final var that = (VirtualBlobValue) obj;
        return Arrays.equals(data, that.data);
    }

    @Override
    public String toString() {
        return "VirtualBlobValue{" + "data=" + Arrays.toString(data) + '}';
    }
}
