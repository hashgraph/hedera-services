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

package com.swirlds.merkledb.files.hashmap;

import static com.swirlds.base.units.UnitConstants.BYTES_PER_LONG;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;

/**
 * A key serializer used by {@link HalfDiskVirtualKeySet} when MerkleDb is operating in long key
 * mode. This key serializer only implements methods require to serialize a long key, and is not a
 * general purpose key serializer.
 */
@ConstructableIgnored
public class VirtualKeySetSerializer implements KeySerializer<VirtualLongKey> {

    @Override
    public long getClassId() {
        // Class ID / version aren't used for this class
        return 0;
    }

    @Override
    public int getVersion() {
        // Class ID / version aren't used for this class
        return 0;
    }

    @Override
    public long getCurrentDataVersion() {
        // Class ID / version aren't used for this class
        return 0;
    }

    @Override
    public int getSerializedSize() {
        return BYTES_PER_LONG;
    }

    @Override
    public void serialize(@NonNull final VirtualLongKey data, @NonNull final WritableSequentialData out) {
        out.writeLong(data.getKeyAsLong());
    }

    @Override
    @Deprecated
    public void serialize(final VirtualLongKey data, final ByteBuffer buffer) {
        buffer.putLong(data.getKeyAsLong());
    }

    @Override
    public VirtualLongKey deserialize(@NonNull final ReadableSequentialData in) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public VirtualLongKey deserialize(final ByteBuffer buffer, final long dataVersion) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(@NonNull final BufferedData buffer, @NonNull final VirtualLongKey keyToCompare) {
        return buffer.readLong() == keyToCompare.getKeyAsLong();
    }

    @Override
    @Deprecated
    public boolean equals(final ByteBuffer buffer, final int dataVersion, final VirtualLongKey keyToCompare) {
        return buffer.getLong() == keyToCompare.getKeyAsLong();
    }
}
