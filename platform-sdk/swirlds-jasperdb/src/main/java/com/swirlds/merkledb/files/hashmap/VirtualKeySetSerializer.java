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

package com.swirlds.merkledb.files.hashmap;

import static com.swirlds.common.utility.Units.BYTES_PER_LONG;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A key serializer used by {@link HalfDiskVirtualKeySet} when MerkleDb is operating in long key
 * mode. This key serializer only implements methods require to serialize a long key, and is not a
 * general purpose key serializer.
 */
@ConstructableIgnored
public class VirtualKeySetSerializer implements KeySerializer<VirtualKey> {

    /** {@inheritDoc} */
    @Override
    public int getSerializedSize() {
        return BYTES_PER_LONG;
    }

    /** {@inheritDoc} */
    @Override
    public int serialize(final VirtualKey data, final ByteBuffer buffer) throws IOException {
        buffer.putLong(((VirtualLongKey) data).getKeyAsLong());
        return BYTES_PER_LONG;
    }

    /** {@inheritDoc} */
    @Override
    public VirtualKey deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override
    public long getCurrentDataVersion() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public int deserializeKeySize(final ByteBuffer buffer) {
        return BYTES_PER_LONG;
    }

    @Override
    public long getClassId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final ByteBuffer buffer, final int dataVersion, final VirtualKey keyToCompare)
            throws IOException {

        return buffer.getLong() == ((VirtualLongKey) keyToCompare).getKeyAsLong();
    }
}
