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

package com.swirlds.merkledb.utilities;

import static com.hedera.pbj.runtime.ProtoParserTools.TAG_FIELD_OFFSET;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import java.util.function.Consumer;

public class ProtoUtils {

    // Copied from ProtoConstants
    public static final int WIRE_TYPE_VARINT = 0;

    // Copied from ProtoConstants
    public static final int WIRE_TYPE_FIXED_64_BIT = 1;

    // Copied from ProtoConstants
    public static final int WIRE_TYPE_DELIMITED = 2;

    // Copied from ProtoConstants
    public static final int WIRE_TYPE_FIXED_32_BIT = 5;

    // Copied from ProtoWriterTools
    private static final int MAX_VARINT_SIZE = 10;

    // Copied from ProtoWriterTools
    public static int sizeOfTag(final FieldDefinition field, final int wireType) {
        return sizeOfVarInt32((field.number() << ProtoParserTools.TAG_FIELD_OFFSET) | wireType);
    }

    // Copied from ProtoWriterTools
    public static int sizeOfUnsignedVarInt32(final int value) {
        if ((value & (~0 << 7)) == 0) return 1;
        if ((value & (~0 << 14)) == 0) return 2;
        if ((value & (~0 << 21)) == 0) return 3;
        if ((value & (~0 << 28)) == 0) return 4;
        return 5;
    }

    // Copied from ProtoWriterTools
    public static int sizeOfVarInt32(final int value) {
        if (value >= 0) {
            return sizeOfUnsignedVarInt32(value);
        } else {
            // Must sign-extend.
            return 10; // MAX_VARINT_SIZE;
        }
    }

    // Copied from ProtoWriterTools
    public static int sizeOfUnsignedVarInt64(long value) {
        // handle two popular special cases up front ...
        if ((value & (~0L << 7)) == 0L) return 1;
        if (value < 0L) return 10;
        // ... leaving us with 8 remaining, which we can divide and conquer
        int n = 2;
        if ((value & (~0L << 35)) != 0L) {
            n += 4;
            value >>>= 28;
        }
        if ((value & (~0L << 21)) != 0L) {
            n += 2;
            value >>>= 14;
        }
        if ((value & (~0L << 14)) != 0L) {
            n += 1;
        }
        return n;
    }

    public static int sizeOfVarInt64(final long value) {
        if (value >= 0) {
            return sizeOfUnsignedVarInt64(value);
        } else {
            // Must sign-extend.
            return 10; // MAX_VARINT_SIZE;
        }
    }

    public static void writeTag(final WritableSequentialData out, final FieldDefinition field) {
        final int wireType =
                switch (field.type()) {
                    case INT32, UINT32, INT64, UINT64 -> WIRE_TYPE_VARINT;
                    case FIXED32 -> WIRE_TYPE_FIXED_32_BIT;
                    case FIXED64 -> WIRE_TYPE_FIXED_64_BIT;
                    case BYTES, MESSAGE -> WIRE_TYPE_DELIMITED;
                    default -> throw new UnsupportedOperationException("Field type not supported: " + field.type());
                };
        out.writeVarInt((field.number() << TAG_FIELD_OFFSET) | wireType, false);
    }

    // Copied from ProtoWriterTools
    public static int sizeOfDelimited(final FieldDefinition field, final int length) {
        assert (field.type() == FieldType.BYTES)
                || (field.type() == FieldType.STRING)
                || (field.type() == FieldType.MESSAGE);
        return Math.toIntExact(sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfVarInt32(length) + length);
    }

    public static <T extends WritableSequentialData> void writeDelimited(
            final T out, final FieldDefinition field, final int size, final Consumer<T> writer) {
        writeTag(out, field);
        out.writeVarInt(size, false);
        writer.accept(out);
    }
}
