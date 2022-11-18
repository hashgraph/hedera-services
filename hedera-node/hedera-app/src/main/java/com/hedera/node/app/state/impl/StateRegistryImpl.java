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
package com.hedera.node.app.state.impl;

import com.hedera.node.app.spi.state.StateRegistry;
import com.hedera.node.app.spi.state.StateRegistryCallback;
import com.hedera.node.app.state.merkle.ServiceStateNode;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * An implementation of the {@link StateRegistry} based on merkle tree. Each instance of this class
 * acts as a different namespace. A new instance should be provided to each service instance,
 * thereby ensuring that each has its own unique namespace and cannot collide (intentionally or
 * accidentally) with others.
 *
 * @see StateRegistry
 */
@NotThreadSafe
public final class StateRegistryImpl implements StateRegistry {
    /**
     * The root node onto which all state for a service will be registered. This is not the root of
     * the entire merkle tree, but it is the root of the tree for this namespace.
     */
    private final ServiceStateNode serviceMerkle;

    /**
     * Create a new instance.
     *
     * @param serviceMerkle The {@link ServiceStateNode} instance for this registry to use. Cannot
     *     be null.
     */
    public StateRegistryImpl(@Nonnull ServiceStateNode serviceMerkle) {
        this.serviceMerkle = Objects.requireNonNull(serviceMerkle);
    }

    /** {@inheritDoc} */
    @Override
    public <K, V> void registerOrMigrate(
            @Nonnull final String stateKey,
            @Nonnull final StateRegistryCallback<K, V> createOrMigrate) {
        // Look up the existing state
        final var existingMerkle = serviceMerkle.find(stateKey);

        // Get the new state to use from the createOrMigrate lambda
        final MutableStateBase<K, V> existingState = asMutableStateBase(stateKey, existingMerkle);
        final var newState =
                (MutableStateBase<K, V>)
                        createOrMigrate.apply(
                                new StateDefinitionBuilder(stateKey), Optional.ofNullable(existingState));

        // The user doesn't want the existing state, so remove it.
        if (newState == null || newState != existingState) {
            serviceMerkle.remove(stateKey);
        }

        // Add the new state in
        if (newState != null) {
            serviceMerkle.put(stateKey, newState.merkleNode());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <K, V> MutableStateBase<K, V> asMutableStateBase(
            String stateKey, MerkleNode merkleNode) {
        if (merkleNode == null) {
            return null;
        }

        if (merkleNode instanceof MerkleMap mmap) {
            return new InMemoryState(stateKey, mmap);
        } else {
            return new OnDiskState(stateKey, (VirtualMap) merkleNode);
        }
    }
}
