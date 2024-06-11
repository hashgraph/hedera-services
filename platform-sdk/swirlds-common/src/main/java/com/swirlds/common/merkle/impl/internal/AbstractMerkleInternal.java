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

package com.swirlds.common.merkle.impl.internal;

import static com.swirlds.common.io.streams.SerializableStreamConstants.NULL_CLASS_ID;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_NODE_CHILD;

import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.base.state.MutabilityException;
import com.swirlds.common.exceptions.ReferenceCountException;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildTypeException;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.merkle.interfaces.MerkleParent;
import com.swirlds.common.merkle.proto.MerkleNodeProtoFields;
import com.swirlds.common.merkle.proto.MerkleProtoUtils;
import com.swirlds.common.merkle.proto.ProtoSerializableNode;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.utility.ValueReference;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * This abstract class implements boiler plate functionality for a binary {@link MerkleInternal} (i.e. an internal
 * node with 2 or fewer children). Classes that implement (@link MerkleInternal} are not required to extend an
 * abstract class such as this or {@link PartialNaryMerkleInternal}, but absent a reason it is recommended to do so
 * in order to avoid re-implementation of this code.
 */
public abstract sealed class AbstractMerkleInternal extends AbstractMerkleNode
        implements MerkleParent, ProtoSerializableNode
        permits PartialBinaryMerkleInternal, PartialNaryMerkleInternal {

    /**
     * Constructor for AbstractMerkleInternal.
     */
    protected AbstractMerkleInternal() {}

    /**
     * Copy constructor. Initializes internal variables and copies the route. Does not copy children or other metadata.
     */
    protected AbstractMerkleInternal(final AbstractMerkleInternal that) {
        super(that);
    }

    protected AbstractMerkleInternal(final @NonNull ReadableSequentialData in, final Path artifactsDir)
            throws MerkleSerializationException {
        super(in, artifactsDir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLeaf() {
        return false;
    }

    /**
     * This is an implementation specific version of setChild.
     *
     * @param index
     * 		which child position is going to be updated
     * @param child
     * 		new node to attach
     */
    protected abstract void setChildInternal(final int index, final MerkleNode child);

    /**
     * Allow N-Ary and Binary Merkle classes to make space as appropriate.
     *
     * @param index
     * 		in the N-Ary case, expand the array to accommodate this many children
     * 		in the Binary case, this is a NOP
     */
    protected abstract void allocateSpaceForChild(final int index);

    /**
     * Check whether the requested index is in valid range [0, maximum child count).
     * In the Binary case, this is a NOP as the error testing happens in getChild(index).
     *
     * @param index
     * 		- child position to verify is legal
     */
    protected abstract void checkChildIndexIsValid(final int index);

    /**
     * {@inheritDoc}
     */
    @Override
    public void setChild(
            final int index, final MerkleNode child, final MerkleRoute childRoute, final boolean childMayBeImmutable) {

        throwIfInvalidState();

        checkChildIndexIsValid(index);

        throwIfInvalidChild(index, child, childMayBeImmutable);

        allocateSpaceForChild(index);

        final MerkleNode oldChild = getChild(index);
        if (oldChild == child) {
            return;
        }
        // When children change the hash needs to be invalidated.
        // Self hashing nodes are required to manage their own hash invalidation.
        if (!isSelfHashing()) {
            invalidateHash();
        }

        // Decrement the reference count of the original child
        if (oldChild != null) {
            oldChild.release();
        }

        if (child != null) {
            // Increment the reference count of the new child
            child.reserve();

            if (childRoute == null) {
                child.setRoute(computeRouteForChild(index));
            } else {
                child.setRoute(childRoute);
            }
        }

        setChildInternal(index, child);
    }

    /**
     * Check if a potential child is valid, and throw if it is not valid
     *
     * @param index
     * 		the index of the child
     * @param child
     * 		the child to be added
     * @param childMayBeImmutable
     * 		if true then the child is permitted to be immutable, if false then it is not permitted
     */
    private void throwIfInvalidChild(final int index, final MerkleNode child, final boolean childMayBeImmutable) {
        long classId = NULL_CLASS_ID;

        if (child != null) {
            classId = child.getClassId();

            if (!childMayBeImmutable && child.isImmutable()) {
                throw new MutabilityException("Immutable child can not be added to parent. parent = "
                        + this.getClass().getName() + ", child = "
                        + child.getClass().getName());
            }
        }

        if (!childHasExpectedType(index, classId)) {
            throw new IllegalChildTypeException(index, classId, this.getClass().getName());
        }
    }

    /**
     * Check if the given class ID is valid for a particular child.
     *
     * @param index
     * 		The child index.
     * @param childClassId
     * 		The class id of the child. May be NULL_CLASS_ID if the child is null.
     * @return true if the child has the appropriate type for the given version.
     */
    protected boolean childHasExpectedType(final int index, final long childClassId) {
        return true;
    }

    private void throwIfInvalidState() {
        if (isImmutable()) {
            throw new MutabilityException(
                    "Can not set child on immutable parent. " + this.getClass().getName());
        }
        if (isDestroyed()) {
            throw new ReferenceCountException(
                    "Can not set child on destroyed parent. " + this.getClass().getName());
        }
    }

    /**
     * Compute and construct a new route for a child at a given position.
     * Recycles the route of the existing child if possible.
     *
     * @param index
     * 		the index of the child
     */
    private MerkleRoute computeRouteForChild(final int index) {
        MerkleRoute childRoute = null;
        if (getNumberOfChildren() > index) {
            final MerkleNode oldChild = getChild(index);
            if (oldChild != null) {
                childRoute = oldChild.getRoute();
            }
        }
        if (childRoute == null) {
            childRoute = getRoute().extendRoute(index);
        }
        return childRoute;
    }

    /**
     * {@inheritDoc}
     *
     * Deserialize from a list of children
     *
     * @param children
     * 		A list of children.
     * @param version
     * 		Version (e.g. format) of the deserialized data.
     */
    @Override
    public void addDeserializedChildren(final List<MerkleNode> children, final int version) {
        for (int childIndex = 0; childIndex < children.size(); childIndex++) {
            setChild(childIndex, children.get(childIndex));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final void onDestroy() {
        destroyNode();
        for (int index = 0; index < getNumberOfChildren(); index++) {
            final MerkleNode child = getChild(index);
            if (child != null) {
                child.release();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidateHash() {
        setHash(null);
    }

    /**
     * {@inheritDoc}
     *
     * WARNING: setting the route on an internal node with children requires a full iteration of the subtree.
     * For large trees this may be very expensive.
     */
    @Override
    public void setRoute(final MerkleRoute route) {
        if (!getRoute().equals(route)) {
            super.setRoute(route);
            updateChildRoutes(route);
        }
    }

    /**
     * If there are children, fix the routes of the children.
     *
     * @param route
     * 		this node's new route
     */
    protected void updateChildRoutes(final MerkleRoute route) {
        for (int index = 0; index < getNumberOfChildren(); index++) {
            final MerkleNode child = getChild(index);
            if (child != null) {
                child.setRoute(getRoute().extendRoute(index));
            }
        }
    }

    // Protobuf serialization

    @Override
    public int getProtoSizeInBytes() {
        int size = 0;
        // Hash
        size += MerkleProtoUtils.getHashSizeInBytes(getHash());
        // Children
        for (int i = 0; i < getNumberOfChildren(); i++) {
            size += getProtoChildSizeInBytes(i);
        }
        // Own data
        size += getProtoSelfSizeInBytes();
        return size;
    }

    protected int getProtoChildSizeInBytes(final int index) {
        final MerkleNode child = getChild(index);
        if (child != null) {
            final int childSize = child.getProtoSizeInBytes();
            if (childSize != 0) {
                return ProtoWriterTools.sizeOfDelimited(FIELD_NODE_CHILD, childSize);
            }
        }
        return 0;
    }

    protected int getProtoSelfSizeInBytes() {
        // Must be in sync with protoSerializeSelf()
        return 0;
    }

    @Override
    protected boolean protoDeserializeField(
            final @NonNull ReadableSequentialData in,
            final Path artifactsDir,
            final int fieldTag)
            throws MerkleSerializationException {
        if (super.protoDeserializeField(in, artifactsDir, fieldTag)) {
            return true;
        }
        final int fieldNum = fieldTag >> ProtoParserTools.TAG_FIELD_OFFSET;
        if (fieldNum == MerkleNodeProtoFields.NUM_NODE_CHILD) {
            assert (fieldTag & ProtoConstants.TAG_WIRE_TYPE_MASK) == ProtoConstants.WIRE_TYPE_DELIMITED.ordinal();
            final int length = in.readVarInt(false);
            final long oldLimit = in.limit();
            try {
                in.limit(in.position() + length);
                final MerkleNode child = protoDeserializeNextChild(in, artifactsDir);
                if (child == null) {
                    throw new MerkleSerializationException("Unknown child node: " + fieldTag);
                }
                // Assuming this method is called in the same order as children are in the stream
                setChild(getNumberOfChildren(), child);
            } finally {
                in.limit(oldLimit);
            }
            return true;
        }
        return false;
    }

    // TODO: current assumption is internals can't have null child nodes
    protected MerkleNode protoDeserializeNextChild(
            final @NonNull ReadableSequentialData in,
            final Path artifactsDir)
            throws MerkleSerializationException {
        throw new UnsupportedOperationException("TO IMPLEMENT: " + getClass().getName() + ".protoDeserializeNextChild()");
    }

    @Override
    public void protoSerialize(final @NonNull WritableSequentialData out, final Path artifactsDir)
            throws MerkleSerializationException {
        final long startPos = out.position();
        // Node hash
        MerkleProtoUtils.protoWriteHash(out, getHash());
        // Children
        for (int i = 0; i < getNumberOfChildren(); i++) {
            protoSerializeChild(out, artifactsDir, i);
        }
        // Own data
        protoSerializeSelf(out, artifactsDir);
        assert out.position() == startPos + getProtoSizeInBytes();
    }

    protected void protoSerializeChild(
            final @NonNull WritableSequentialData out,
            final Path artifactsDir,
            final int index)
            throws MerkleSerializationException {
        final MerkleNode child = getChild(index);
        if (child == null) {
            throw new MerkleSerializationException("Cannot serialize internal node, child is null");
        }
        final int childSize = child.getProtoSizeInBytes();
        if (childSize != 0) {
            final ValueReference<MerkleSerializationException> error = new ValueReference<>();
            // TODO: improve writeDelimited() to throw a checked exception
            ProtoWriterTools.writeDelimited(out, FIELD_NODE_CHILD, child.getProtoSizeInBytes(),
                    w -> {
                        try {
                            child.protoSerialize(w, artifactsDir);
                        } catch (final MerkleSerializationException e) {
                            error.setValue(e);
                        }
                    });
            final MerkleSerializationException e = error.getValue();
            if (e != null) {
                throw e;
            }
        }
    }

    protected void protoSerializeSelf(final @NonNull WritableSequentialData out, final Path artifactsDir)
            throws MerkleSerializationException {
        // No op by default. Must be in sync with getProtoSelfSizeInBytes()
    }

}
