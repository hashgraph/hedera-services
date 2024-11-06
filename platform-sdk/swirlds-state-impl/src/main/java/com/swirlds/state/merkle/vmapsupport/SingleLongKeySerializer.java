/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.merkle.vmapsupport;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;
import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.virtualmap.serialize.KeySerializer;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Virtual key serializer for {@link SingleLongKey} keys. Keys are serialized as a single
 * unit64 protobuf field.
 */
public class SingleLongKeySerializer implements KeySerializer<SingleLongKey> {

    private static final long CLASS_ID = 0xbbbe23fdbce1007dL;

    private static final FieldDefinition FIELD = new FieldDefinition("value", FieldType.UINT64, false, true, false, 11);

    // Serializer info

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    // Key info

    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    // Key serialization

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getSerializedSize(@NonNull final SingleLongKey key) {
        int size = 0;
        if (key.getValue() != 0) {
            size += ProtoWriterTools.sizeOfTag(FIELD) + ProtoWriterTools.sizeOfVarInt64(key.getValue());
        }
        return size;
    }

    @Override
    public int getTypicalSerializedSize() {
        return 1 /* tag */ + 4 /* avg var long size */;
    }

    // Key serialization

    @Override
    public void serialize(@NonNull final SingleLongKey key, final @NonNull WritableSequentialData out) {
        if (key.getValue() != 0) {
            ProtoWriterTools.writeTag(out, FIELD);
            out.writeVarLong(key.getValue(), false);
        }
    }

    // Key deserialization

    @Override
    public SingleLongKey deserialize(@NonNull final ReadableSequentialData in) {
        long value = 0;
        if (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int number = tag >>> ProtoParserTools.TAG_FIELD_OFFSET;
            final int type = tag & ProtoConstants.TAG_WIRE_TYPE_MASK;
            if ((number != FIELD.number()) || (type != ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal())) {
                throw new RuntimeException("Unknown tag: " + tag);
            }
            value = in.readVarLong(false);
        }
        return new SingleLongKey(value);
    }

    @Override
    public boolean equals(@NonNull final BufferedData in, @NonNull final SingleLongKey keyToCompare) {
        if (!in.hasRemaining()) {
            return keyToCompare.getValue() == 0;
        }
        final int tag = in.readVarInt(false);
        final int number = tag >>> ProtoParserTools.TAG_FIELD_OFFSET;
        if (number != FIELD.number()) {
            return false;
        }
        final int type = tag & ProtoConstants.TAG_WIRE_TYPE_MASK;
        if (type != ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal()) {
            return false;
        }
        return keyToCompare.getValue() == in.readVarLong(false);
    }
}
