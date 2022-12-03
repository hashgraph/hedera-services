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

import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.NotImplementedException;

public class HederaStateImpl extends PartialNaryMerkleInternal
        implements MerkleInternal, SwirldState2, HederaState {

    // For serialization
    private static final long CLASS_ID = 29399209029302L;
    private static final int VERSION_1 = 1;
    private static final int CURRENT_VERSION = VERSION_1;

    private BiConsumer<Round, SwirldDualState> onHandleConsensusRound;

    /**
     * Create a new HederaState! This can be called either explicitly or as part of saved state
     * loading.
     */
    public HederaStateImpl() {}

    /**
     * Private constructor for fast-copy.
     *
     * @param from The other state to fast-copy from. Cannot be null.
     */
    private HederaStateImpl(@NonNull final HederaStateImpl from) {
        // Copy the Merkle route from the source instance
        super(from);

        // Copy the non-null Merkle children from the source (should also be handled by super, TBH).
        for (int childIndex = 0, n = from.getNumberOfChildren(); childIndex < n; childIndex++) {
            final var childToCopy = from.getChild(childIndex);
            if (childToCopy != null) {
                setChild(childIndex, childToCopy.copy());
            }
        }

        // **MOVE** over the listener
        this.onHandleConsensusRound = from.onHandleConsensusRound;
        from.onHandleConsensusRound = null;
    }

    @Override
    public ReadableStates createReadableStates(@NonNull final String serviceName) {
        //        final var opt = getServiceStateNode(serviceName);
        //        if (opt.isEmpty()) {
        //            return null; // Null, or optional??
        //        }
        //
        //        return new ReadableStates() {
        //            @NonNull
        //            @Override
        //            public <K, V, S extends ReadableState<K, V>> S get(@NonNull String stateKey) {
        //                final var service = opt.get();
        //                final var merkleNode = service.find(stateKey);
        //                if (merkleNode instanceof MerkleMap) {
        //                    //noinspection unchecked,rawtypes
        //                    return (S) new InMemoryState(stateKey, (MerkleMap) merkleNode);
        //                } else if (merkleNode instanceof VirtualMap) {
        //                    //noinspection unchecked,rawtypes
        //                    return (S) new OnDiskState<>(stateKey, (VirtualMap) merkleNode);
        //                } else {
        //                    throw new IllegalArgumentException("Cannot find state for '" +
        // stateKey + "'");
        //                }
        //            }
        //        };

        return null; // TODO
    }

    @Override
    public WritableStates createWritableStates(@NonNull final String serviceName) {
        throwIfImmutable();
        final var opt = getServiceStateNode(serviceName);
        if (opt.isEmpty()) {
            return null; // Null, or optional??
        }

        return new WritableStates() {
            @NonNull
            @Override
            public <K, V> WritableState<K, V> get(@NonNull String stateKey) {
                //                final var service = opt.get();
                //                final var merkleNode = service.find(stateKey);
                //                if (merkleNode instanceof MerkleMap) {
                //                    //noinspection unchecked,rawtypes
                //                    return new InMemoryState(stateKey, (MerkleMap) merkleNode);
                //                } else if (merkleNode instanceof VirtualMap) {
                //                    //noinspection unchecked,rawtypes
                //                    return new OnDiskState<>(stateKey, (VirtualMap) merkleNode);
                //                } else {
                //                    throw new IllegalArgumentException("Cannot find state for '" +
                // stateKey + "'");
                //                }
                return null; // TODO
            }
        };
    }

    /**
     * Set a callback to be invoked once per consensus round.
     *
     * @param onHandleConsensusRound The callback to invoke. If null, unsets the callback.
     */
    public void setOnHandleConsensusRound(
            @Nullable final BiConsumer<Round, SwirldDualState> onHandleConsensusRound) {
        throwIfImmutable();
        this.onHandleConsensusRound = onHandleConsensusRound;
    }

    /**
     * Adds the given {@link ServiceStateNode} to the state merkle tree. This call
     * <strong>only</strong> takes effect if there is not already a node with the same {@code
     * serviceName} on the state. Otherwise, the call is a no-op.
     *
     * @param node The node to add. Cannot be null.
     */
    public void addServiceStateNode(@NonNull final ServiceStateNode node) {
        throwIfImmutable();
        // See if there is already a node for this, if not, add it.
        final var optNode = getServiceStateNode(node.getServiceName());
        if (optNode.isEmpty()) {
            // Didn't find it, so we will add a new one
            setChild(getNumberOfChildren(), node);
        }
    }

    /**
     * Finds and returns the {@link ServiceStateNode} with a matching {@code serviceName}, if there
     * is one.
     *
     * @param serviceName The service name. Cannot be null.
     * @return An {@link Optional} that is empty if nothing was found, or it contains the matching
     *     {@link ServiceStateNode}.
     */
    @NonNull
    public Optional<ServiceStateNode> getServiceStateNode(@NonNull final String serviceName) {
        Objects.requireNonNull(serviceName);

        // Find a node with this service name, if there is one.
        final int numNodes = getNumberOfChildren();
        for (int i = 0; i < numNodes; i++) {
            final var node = getChild(i);
            if (node instanceof ServiceStateNode ssn
                    && Objects.equals(ssn.getServiceName(), serviceName)) {
                return Optional.of(ssn);
            }
        }

        return Optional.empty();
    }

    /**
     * Adds the given {@link ServiceStateNode} to the state merkle tree. This call
     * <strong>only</strong> takes effect if there is not already a node with the same {@code
     * serviceName} on the state. Otherwise, the call is a no-op.
     *
     * @param serviceName The service name. Cannot be null.
     */
    public void removeServiceStateNode(@NonNull final String serviceName) {
        throwIfImmutable();
        Objects.requireNonNull(serviceName);
        // See if there is already a node for this, if so, remove it.
        for (int i = 0; i < getNumberOfChildren(); i++) {
            final var node = getChild(i);
            if (node instanceof ServiceStateNode ssn
                    && Objects.equals(ssn.getServiceName(), serviceName)) {
                setChild(i, null);
                return;
            }
        }
    }

    @Override
    public HederaStateImpl copy() {
        throwIfImmutable();
        throwIfDestroyed();
        setImmutable(true);
        return new HederaStateImpl(this);
    }

    @Override
    public AddressBook getAddressBookCopy() {
        throw new NotImplementedException("Not yet implemented");
    }

    @Override
    public void handleConsensusRound(Round round, SwirldDualState swirldDualState) {
        if (onHandleConsensusRound != null) {
            onHandleConsensusRound.accept(round, swirldDualState);
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
}
