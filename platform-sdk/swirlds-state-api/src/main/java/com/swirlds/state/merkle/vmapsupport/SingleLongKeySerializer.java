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
        return ProtoWriterTools.sizeOfTag(FIELD) + ProtoWriterTools.sizeOfVarInt64(key.getValue());
    }

    @Override
    public int getTypicalSerializedSize() {
        return 1 /* tag */ + 4 /* avg long size */;
    }

    // Key serialization

    @Override
    public void serialize(@NonNull final SingleLongKey key, final @NonNull WritableSequentialData out) {
        ProtoWriterTools.writeTag(out, FIELD);
        out.writeVarLong(key.getValue(), false);
    }

    // Key deserialization

    @Override
    public SingleLongKey deserialize(@NonNull final ReadableSequentialData in) {
        final int tag = in.readVarInt(false);
        final int number = tag >>> ProtoParserTools.TAG_FIELD_OFFSET;
        final int type = tag & ProtoConstants.TAG_WIRE_TYPE_MASK;
        if ((number != FIELD.number()) || (type != FIELD.type().ordinal())) {
            throw new RuntimeException("Unknown tag: " + tag);
        }
        final long value = in.readVarLong(false);
        return new SingleLongKey(value);
    }

    @Override
    public boolean equals(@NonNull final BufferedData in, @NonNull final SingleLongKey keyToCompare) {
        final int tag = in.readVarInt(false);
        final int number = tag >>> ProtoParserTools.TAG_FIELD_OFFSET;
        if (number != FIELD.number()) {
            return false;
        }
        final int type = tag & ProtoConstants.TAG_WIRE_TYPE_MASK;
        if (type != FIELD.type().ordinal()) {
            return false;
        }
        return keyToCompare.getValue() == in.readVarLong(false);
    }
}
