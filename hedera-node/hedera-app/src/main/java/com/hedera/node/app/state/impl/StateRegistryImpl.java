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

import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.StateRegistry;
import com.hedera.node.app.spi.state.WritableState;
import com.hedera.node.app.state.merkle.ServiceStateNode;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * An implementation of the {@link StateRegistry} based on merkle tree. Each instance of this class
 * acts as a different namespace. A new instance should be provided to each service instance,
 * thereby ensuring that each has its own unique namespace and cannot collide (intentionally or
 * accidentally) with others.
 *
 * @see StateRegistry
 */
/*@NotThreadSafe*/
public final class StateRegistryImpl implements StateRegistry {
    /**
     * The root node onto which all state for a service will be registered. This is not the root of
     * the entire merkle tree, but it is the root of the tree for this namespace.
     */
    private final ServiceStateNode serviceMerkle;

    private final SoftwareVersion currentVersion;
    private final SoftwareVersion previousVersion;

    /**
     * Create a new instance.
     *
     * @param serviceMerkle The {@link ServiceStateNode} instance for this registry to use. Cannot
     *     be null.
     */
    public StateRegistryImpl(
            @NonNull ServiceStateNode serviceMerkle,
            @NonNull SoftwareVersion currentVersion,
            @Nullable SoftwareVersion previousVersion) {
        this.serviceMerkle = Objects.requireNonNull(serviceMerkle);
        this.currentVersion = Objects.requireNonNull(currentVersion);
        this.previousVersion = previousVersion;
    }

    @NonNull
    @Override
    public SoftwareVersion getCurrentVersion() {
        return currentVersion;
    }

    @Nullable
    @Override
    public SoftwareVersion getExistingVersion() {
        return previousVersion;
    }

    @Override
    public <K, V> WritableState<K, V> getState(@NonNull String stateKey) {
        final var existingMerkle = serviceMerkle.find(stateKey);

        // Get the new state to use from the createOrMigrate lambda
        return asMutableStateBase(stateKey, existingMerkle);
    }

    @Override
    public StateDefinition defineNewState(@NonNull String stateKey) {
        return new StateDefinitionBuilder(
                stateKey, (merkleNode) -> serviceMerkle.put(stateKey, merkleNode));
    }

    @Override
    public void removeState(@NonNull String stateKey) {
        serviceMerkle.remove(stateKey);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <K, V> MutableStateBase<K, V> asMutableStateBase(
            String stateKey, MerkleNode merkleNode) {
        if (merkleNode == null) {
            return null;
        }

        if (merkleNode instanceof MerkleMap mmap) {
            return new InMemoryState(stateKey, mmap);
        } else if (merkleNode instanceof VirtualMap vmap) {
            return new OnDiskState(stateKey, vmap);
        } else {
            throw new IllegalStateException("The merkle node was of an unsupported type!");
        }
    }
}
