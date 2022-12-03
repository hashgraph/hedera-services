/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.merkle;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * An internal merkle node acting as the root for <b>all</b> state related to a particular service
 * module. Each service module has its own namespace within which all merkle state is stored
 * (similar to how each microservice in a system may have a different namespace or schema within the
 * database). The {@link HederaStateImpl} is the root state of the application, and has as children
 * instances of this class.
 */
public final class ServiceStateNode extends PartialNaryMerkleInternal implements MerkleInternal {
    // For serialization
    private static final long CLASS_ID = 2202034923L;
    private static final int VERSION_1 = 1;
    private static final int CURRENT_VERSION = VERSION_1;

    /**
     * Standardized child index at which to find the "serviceName" of the service for which this
     * node exists.
     */
    private static final int NAME_CHILD_INDEX = 0;

    /** DO NOT CALL THIS CONSTRUCTOR. It exists only for deserialization. */
    public ServiceStateNode() {}

    /**
     * Create a new ServiceStateNode.
     *
     * @param serviceName The name of the service for which this node holds state.
     */
    public ServiceStateNode(@NonNull final String serviceName) {
        Objects.requireNonNull(serviceName);
        setChild(NAME_CHILD_INDEX, new StringLeaf(serviceName));
    }

    /**
     * Create a fast copy.
     *
     * @param from The node to copy from
     */
    private ServiceStateNode(@NonNull final ServiceStateNode from) {
        super(from);

        // Copy the non-null Merkle children from the source (should also be handled by super, TBH).
        for (int childIndex = 0, n = from.getNumberOfChildren(); childIndex < n; childIndex++) {
            final var childToCopy = from.getChild(childIndex);
            if (childToCopy != null) {
                setChild(childIndex, childToCopy.copy());
            }
        }
    }

    /**
     * Gets the name of the service associated with this state.
     *
     * @throws IllegalStateException If, somehow, the service name was not set.
     * @return The name of the service. This will never be null.
     */
    @NonNull
    public String getServiceName() {
        // It should not be possible for a ServiceStateNode to exist without the "name". It could
        // only happen if the default constructor were used, when it shouldn't have been.
        final StringLeaf nameLeaf = getChild(NAME_CHILD_INDEX);
        if (nameLeaf == null) {
            throw new IllegalStateException("Unexpectedly, the service name is null!");
        }

        return nameLeaf.getValue();
    }

    /**
     * Given some state key, look for an associated piece of state (a {@link MerkleNode} of some
     * kind) and return it. If it cannot be found, return null.
     *
     * @param stateKey The key of the state to lookup. Cannot be null.
     * @return The {@link MerkleNode}, if one was found, that corresponds with the state key.
     *     Otherwise, null.
     */
    @Nullable
    public <T extends MerkleNode> T find(@NonNull final String stateKey) {
        Objects.requireNonNull(stateKey);
        final int indexOfNode = indexOf(stateKey);
        return indexOfNode == -1 ? null : getChild(indexOfNode);
    }

    /**
     * Idempotent "put" of the given merkle node into the tree. If there is already a merkle node at
     * this state key location, it will be replaced.
     *
     * @param stateKey The state key. Cannot be null. It must be at least one character in length,
     *     and must be comprised of spaces, numbers, and alphabetic characters in the ascii range.
     * @param merkle The merkle node to set. Cannot be null.
     */
    public void put(@NonNull final String stateKey, @NonNull final MerkleNode merkle) {
        StateUtils.validateStateKey(stateKey);
        final int existingIndex = indexOf(stateKey);
        final int index = existingIndex == -1 ? getNumberOfChildren() : existingIndex;
        setChild(index, new StringLeaf(stateKey));
        setChild(index + 1, merkle);
    }

    /**
     * Removes the state associated with the given state key, if any. This method is idempotent.
     *
     * @param stateKey The state key. Cannot be null.
     */
    public void remove(@NonNull final String stateKey) {
        Objects.requireNonNull(stateKey);
        final int matchingIndex = indexOf(stateKey);
        if (matchingIndex != -1) {
            setChild(matchingIndex - 1, null);
            setChild(matchingIndex, null);
        }
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    @NonNull
    public ServiceStateNode copy() {
        throwIfImmutable();
        throwIfDestroyed();
        setImmutable(true);
        return new ServiceStateNode(this);
    }

    /**
     * Get the index of the state associated with the given state key, or null if there is no such
     * state.
     *
     * @param stateKey The state key, cannot be null
     * @return The index of the associated state, or -1 if there is not any
     */
    private int indexOf(@NonNull final String stateKey) {
        Objects.requireNonNull(stateKey);

        // The first child of ServiceStateNode is a StringLeaf containing the serviceName. The
        // subsequent children come in pairs -- the first is a StringLeaf with the stateKey,
        // the next is the corresponding MerkleNode.
        final var numChildren = getNumberOfChildren();
        for (int i = 1; i < numChildren - 1; i += 2) {
            final StringLeaf idNode = getChild(i);
            if (idNode != null && stateKey.equals(idNode.getValue())) {
                return i + 1;
            }
        }

        return -1;
    }
}
