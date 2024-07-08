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

import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_SINGLETONSTATE_LABEL;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_SINGLETONSTATE_VALUE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_SINGLETONSTATE_LABEL;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_SINGLETONSTATE_VALUE;
import static com.swirlds.platform.state.merkle.logging.StateLogger.logSingletonRead;
import static com.swirlds.platform.state.merkle.logging.StateLogger.logSingletonWrite;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.merkle.utility.DebugIterationEndpoint;
import com.swirlds.common.utility.Labeled;
import com.swirlds.platform.state.merkle.StateUtils;
import com.swirlds.state.spi.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;

/**
 * A merkle node with a string (the label) as the left child, and the merkle node value as the right
 * child. We actually support a raw type (any type!) as the value, and we serialize it and put it
 * into a simple merkle node.
 *
 * @param <T> The value type
 */
@DebugIterationEndpoint
public class SingletonNode<T> extends PartialBinaryMerkleInternal implements Labeled, MerkleInternal {

    private static final long CLASS_ID = 0x3832CC837AB77BFL;
    public static final int CLASS_VERSION = 1;

    // Only used for deserialization
    private Codec<T> codec = null;

    /**
     * @deprecated Only exists for constructable registry as it works today. Remove ASAP!
     */
    @Deprecated(forRemoval = true)
    public SingletonNode() {
        setLeft(new StringLeaf());
        setRight(null);
    }

    public SingletonNode(
            @NonNull final String serviceName,
            @NonNull final String stateKey,
            long classId,
            @NonNull final Codec<T> codec,
            @Nullable final T value) {
        setLeft(new StringLeaf(StateUtils.computeLabel(serviceName, stateKey)));
        setRight(new ValueLeaf<>(classId, codec, value));
    }

    private SingletonNode(@NonNull final SingletonNode<T> other) {
        this.setLeft(other.getLeft().copy());
        this.setRight(other.getRight().copy());
    }

    public SingletonNode(
            @NonNull final ReadableSequentialData in,
            final Path artifactsDir,
            @NonNull final Codec<T> codec)
            throws MerkleSerializationException {
        this.codec = codec;
        protoDeserialize(in, artifactsDir);
    }

    @Override
    public SingletonNode<T> copy() {
        return new SingletonNode<>(this);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    @Override
    public String getLabel() {
        final StringLeaf left = getLeft();
        return left.getLabel();
    }

    public T getValue() {
        final ValueLeaf<T> right = getRight();
        // Log to transaction state log, what was read
        logSingletonRead(getLabel(), right);
        return right.getValue();
    }

    public void setValue(T value) {
        ValueLeaf<T> right = getRight();
        right.setValue(value);
        // Log to transaction state log, what was written
        logSingletonWrite(getLabel(), value);
    }

    // Protobuf serialization

    @Override
    protected boolean isChildNodeProtoTag(final int fieldNum) {
        return (fieldNum == NUM_SINGLETONSTATE_LABEL) ||
                (fieldNum == NUM_SINGLETONSTATE_VALUE);
    }

    @Override
    protected MerkleNode protoDeserializeNextChild(
            @NonNull final ReadableSequentialData in,
            final Path artifactsDir)
            throws MerkleSerializationException {
        final int childrenSoFar = getNumberOfChildren();
        if (childrenSoFar == 0) {
            // FUTURE WORK: check that the label matches the state definition
            return new StringLeaf(in);
        } else if (childrenSoFar == 1) {
            return new ValueLeaf<>(in, codec);
        } else {
            throw new MerkleSerializationException("Too many singleton state child nodes");
        }
    }

    @Override
    protected FieldDefinition getChildProtoField(final int childIndex) {
        return switch (childIndex) {
            case 0 -> FIELD_SINGLETONSTATE_LABEL;
            case 1 -> FIELD_SINGLETONSTATE_VALUE;
            default -> throw new IllegalArgumentException("Unknown singleton state child index: " + childIndex);
        };
    }
}
