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

package com.swirlds.platform.state.merkle.singleton;

import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STRINGLEAF_VALUE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STRINGLEAF_VALUE;

import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.utility.Labeled;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** A leaf in the merkle tree that stores a string as its value. */
public class StringLeaf extends PartialMerkleLeaf implements Labeled, MerkleLeaf {

    private static final long CLASS_ID = 0x9C829FF3B2283L;
    public static final int CLASS_VERSION = 1;

    private String label = "";

    /** Zero-arg constructor. */
    public StringLeaf() {}

    public StringLeaf(@NonNull final String label) {
        setLabel(label);
    }

    /** Copy constructor. */
    private StringLeaf(@NonNull final StringLeaf that) {
        this.label = that.label;
    }

    public StringLeaf(@NonNull final ReadableSequentialData in) throws MerkleSerializationException {
        protoDeserialize(in, null);
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeNormalisedString(label);
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        label = in.readNormalisedString(MAX_LABEL_LENGTH);
    }

    /** {@inheritDoc} */
    @Override
    public StringLeaf copy() {
        return new StringLeaf(this);
    }

    /** {@inheritDoc} */
    @Override
    public String getLabel() {
        return label;
    }

    /** Set the label. */
    public void setLabel(@NonNull final String label) {
        if (label.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Label " + label + " exceeds maximum length of " + MAX_LABEL_LENGTH);
        }
        this.label = label;
    }

    // Protobuf serialization

    @Override
    public int getProtoSizeInBytes() {
        int size = super.getProtoSizeInBytes(); // Includes hash
        if ((label != null) && !label.isEmpty()) {
            size += ProtoWriterTools.sizeOfDelimited(FIELD_STRINGLEAF_VALUE, label.getBytes(StandardCharsets.UTF_8).length);
        }
        return size;
    }

    @Override
    protected boolean protoDeserializeField(
            @NonNull final ReadableSequentialData in,
            final Path artifactsDir,
            final int fieldTag)
            throws MerkleSerializationException {
        if (super.protoDeserializeField(in, artifactsDir, fieldTag)) { // Reads hash
            return true;
        }
        final int fieldNum = fieldTag >> ProtoParserTools.TAG_FIELD_OFFSET;
        final int wireType = fieldTag & ProtoConstants.TAG_WIRE_TYPE_MASK;
        if (fieldNum == NUM_STRINGLEAF_VALUE) {
            if (wireType != ProtoConstants.WIRE_TYPE_DELIMITED.ordinal()) {
                throw new MerkleSerializationException("Unexpected field wire type: " + wireType);
            }
            final int len = in.readVarInt(false);
            final byte[] labelBytes = new byte[len];
            if (in.readBytes(labelBytes) != len) {
                throw new MerkleSerializationException("Failed to read StringLeaf label bytes");
            }
            label = new String(labelBytes, StandardCharsets.UTF_8);
            return true;
        } else {
            throw new MerkleSerializationException("Unknown field: " + fieldTag);
        }
    }

    @Override
    public void protoSerialize(
            @NonNull final WritableSequentialData out,
            final Path artifactsDir)
            throws MerkleSerializationException {
        // Write hash
        super.protoSerialize(out, artifactsDir);
        // Write label, if not null or empty
        if ((label != null) && !label.isEmpty()) {
            final byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
            ProtoWriterTools.writeDelimited(out, FIELD_STRINGLEAF_VALUE, labelBytes.length, o -> {
                o.writeBytes(labelBytes);
            });
        }
    }
}
