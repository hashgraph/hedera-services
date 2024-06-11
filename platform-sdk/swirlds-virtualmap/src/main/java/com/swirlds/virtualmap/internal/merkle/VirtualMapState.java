/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.merkle;

import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VMSTATE_FIRSTLEAFPATH;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VMSTATE_LABEL;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_VMSTATE_LASTLEAFPATH;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VMSTATE_FIRSTLEAFPATH;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VMSTATE_LABEL;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_VMSTATE_LASTLEAFPATH;

import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.Path;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Contains state for a {@link VirtualMap}. This state is stored in memory in the merkle tree as
 * the first (left) child of the VFCMap / {@link VirtualMap}.
 */
public class VirtualMapState extends PartialMerkleLeaf implements MerkleLeaf {

    public static final long CLASS_ID = 0x9e698c13a408250dL;
    private static final int CLASS_VERSION = 1;

    public static final int MAX_LABEL_CHARS = 512;
    public static final int MAX_LABEL_LENGTH = MAX_LABEL_CHARS * 3;

    /**
     * The path of the very first leaf in the tree. Can be null if there are no leaves.
     */
    private long firstLeafPath;

    /**
     * The path of the very last leaf in the tree. Can be null if there are no leaves.
     */
    private long lastLeafPath;

    /**
     * The label for the virtual tree.  Needed to differentiate between different VirtualMaps (for stats).
     */
    private String label;

    /**
     * Create a new {@link VirtualMapState}.
     */
    public VirtualMapState() {
        // Only use this constructor for serialization
        this((String) null);
    }

    /**
     * Create a new {@link VirtualMapState}.
     */
    public VirtualMapState(String label) {
        firstLeafPath = -1;
        lastLeafPath = -1;
        this.label = label;
    }

    /**
     * Create a copy of the {@link VirtualMapState}.
     *
     * @param source
     * 		The map state to copy. Cannot be null.
     */
    private VirtualMapState(final VirtualMapState source) {
        this.firstLeafPath = source.firstLeafPath;
        this.lastLeafPath = source.lastLeafPath;
        this.label = source.label;
    }

    public VirtualMapState(final @NonNull ReadableSequentialData in, final java.nio.file.Path artifactsDir)
            throws MerkleSerializationException {
        protoDeserialize(in, artifactsDir);
    }

    /**
     * Gets the firstLeafPath. Can be {@link Path#INVALID_PATH} if there are no leaves.
     *
     * @return The first leaf path.
     */
    public long getFirstLeafPath() {
        return firstLeafPath;
    }

    /**
     * Set the first leaf path.
     *
     * @param path
     * 		The new path. Can be {@link Path#INVALID_PATH}, or positive. Cannot be 0 or any other negative value.
     * @throws IllegalArgumentException
     * 		If the path is not valid
     */
    public void setFirstLeafPath(final long path) {
        if (path < 1 && path != Path.INVALID_PATH) {
            throw new IllegalArgumentException("The path must be positive, or INVALID_PATH, but was " + path);
        }
        if (path > lastLeafPath) {
            throw new IllegalArgumentException("The firstLeafPath must be less than or equal to the lastLeafPath");
        }
        firstLeafPath = path;
    }

    /**
     * Gets the lastLeafPath. Can be {@link Path#INVALID_PATH} if there are no leaves.
     *
     * @return The last leaf path.
     */
    public long getLastLeafPath() {
        return lastLeafPath;
    }

    /**
     * Set the last leaf path.
     *
     * @param path
     * 		The new path. Can be {@link Path#INVALID_PATH}, or positive. Cannot be 0 or any other negative value.
     * @throws IllegalArgumentException
     * 		If the path is not valid
     */
    public void setLastLeafPath(final long path) {
        if (path < 1 && path != Path.INVALID_PATH) {
            throw new IllegalArgumentException("The path must be positive, or INVALID_PATH, but was " + path);
        }
        if (path < firstLeafPath) {
            throw new IllegalArgumentException("The lastLeafPath must be greater than or equal to the firstLeafPath");
        }
        this.lastLeafPath = path;
    }

    // needs to be callable from VirtualMap.java, which is in the parent package.
    public long getSize() {
        if (firstLeafPath == -1) {
            return 0;
        }

        return lastLeafPath - firstLeafPath + 1;
    }

    // needs to be callable from VirtualMap.java, which is in the parent package.
    public String getLabel() {
        return label;
    }

    // needs to be callable from VirtualMap.java, which is in the parent package.
    public void setLabel(final String label) {
        Objects.requireNonNull(label);
        if (label.length() > MAX_LABEL_CHARS) {
            throw new IllegalArgumentException("Label cannot be greater than 512 characters");
        }
        this.label = label;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(firstLeafPath);
        out.writeLong(lastLeafPath);

        out.writeNormalisedString(label);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        firstLeafPath = in.readLong();
        lastLeafPath = in.readLong();
        label = in.readNormalisedString(MAX_LABEL_LENGTH);
    }

    @Override
    public int getProtoSizeInBytes() {
        int size = 0;
        if (firstLeafPath != 0) {
            size += ProtoWriterTools.sizeOfTag(FIELD_VMSTATE_FIRSTLEAFPATH);
            size += ProtoWriterTools.sizeOfVarInt64(firstLeafPath);
        }
        if (lastLeafPath != 0) {
            size += ProtoWriterTools.sizeOfTag(FIELD_VMSTATE_LASTLEAFPATH);
            size += ProtoWriterTools.sizeOfVarInt64(lastLeafPath);
        }
        if (label != null) {
            final int labelLength = label.getBytes(StandardCharsets.UTF_8).length;
            size += ProtoWriterTools.sizeOfDelimited(FIELD_VMSTATE_LABEL, labelLength);
        }
        return size;
    }

    @Override
    protected boolean protoDeserializeField(
            final @NonNull ReadableSequentialData in,
            final java.nio.file.Path artifactsDir,
            final int fieldTag)
            throws MerkleSerializationException {
        final int fieldNum = fieldTag >> ProtoParserTools.TAG_FIELD_OFFSET;
        if (fieldNum == NUM_VMSTATE_FIRSTLEAFPATH) {
            assert (fieldTag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
            firstLeafPath = in.readVarInt(false);
            return true;
        }
        if (fieldNum == NUM_VMSTATE_LASTLEAFPATH) {
            assert (fieldTag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal();
            lastLeafPath = in.readVarInt(false);
            return true;
        }
        if (fieldNum == NUM_VMSTATE_LABEL) {
            assert (fieldTag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_DELIMITED.ordinal();
            final int length = in.readVarInt(false);
            final byte[] labelBytes = new byte[length];
            if (in.readBytes(labelBytes) != length) {
                throw new MerkleSerializationException("Failed to read VirtualMap state label bytes");
            }
            label = new String(labelBytes, StandardCharsets.UTF_8);
            return true;
        }
        return false;
    }

    @Override
    public void protoSerialize(final @NonNull WritableSequentialData out, final java.nio.file.Path artifactsDir) {
        if (firstLeafPath != 0) {
            ProtoWriterTools.writeTag(out, FIELD_VMSTATE_FIRSTLEAFPATH);
            out.writeVarLong(firstLeafPath, false);
        }
        if (lastLeafPath != 0) {
            ProtoWriterTools.writeTag(out, FIELD_VMSTATE_LASTLEAFPATH);
            out.writeVarLong(lastLeafPath, false);
        }
        if (label != null) {
            final byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
            ProtoWriterTools.writeDelimited(out, FIELD_VMSTATE_LABEL, labelBytes.length,
                    t -> t.writeBytes(labelBytes));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualMapState copy() {
        return new VirtualMapState(this);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("firstLeafPath", firstLeafPath)
                .append("lastLeafPath", lastLeafPath)
                .append("size", getSize())
                .append("label", label)
                .toString();
    }
}
