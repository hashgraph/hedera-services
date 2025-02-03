/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.state.merkle;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.utility.RuntimeObjectRecord;
import com.swirlds.common.utility.RuntimeObjectRegistry;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An implementation of {@link State}.
 *
 * <p>Among {@link MerkleStateRoot}'s child nodes are the various {@link
 * com.swirlds.merkle.map.MerkleMap}'s and {@link com.swirlds.virtualmap.VirtualMap}'s that make up
 * the service's states. Each such child node has a label specified that is computed from the
 * metadata for that state. Since both service names and state keys are restricted to characters
 * that do not include the period, we can use it to separate service name from state key. When we
 * need to find all states for a service, we can do so by iteration and string comparison.
 *
 * <p>NOTE: The implementation of this class must change before we can support state proofs
 * properly. In particular, a wide n-ary number of children is less than ideal, since the hash of
 * each child must be part of the state proof. It would be better to have a binary tree. We should
 * consider nesting service nodes in a MerkleMap, or some other such approach to get a binary tree.
 */
@ConstructableIgnored
public abstract class MerkleStateRoot<T extends MerkleStateRoot<T>> extends PartialNaryMerkleInternal
        implements MerkleInternal, State {

    private static final long CLASS_ID = 0x8e300b0dfdafbb1bL;

    // Migrates from `PlatformState` to State API singleton
    public static final int CURRENT_VERSION = 31;

    /**
     * Used to track the lifespan of this state.
     */
    private final RuntimeObjectRecord registryRecord;

    /**
     * Create a new instance. This constructor must be used for all creations of this class.
     *
     */
    public MerkleStateRoot() {
        this.registryRecord = RuntimeObjectRegistry.createRecord(getClass());
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
    public int getMinimumSupportedVersion() {
        return CURRENT_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroyNode() {
        registryRecord.release();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public T copy() {
        throwIfImmutable();
        throwIfDestroyed();
        setImmutable(true);
        return copyingConstructor();
    }

    protected abstract T copyingConstructor();

    @Override
    public MerkleNode migrate(int version) {
        if (version < getMinimumSupportedVersion()) {
            throw new UnsupportedOperationException("State migration from version " + version + " is not supported."
                    + " The minimum supported version is " + getMinimumSupportedVersion());
        }
        return this;
    }

    /**
     * Returns the number of the current rount
     */
    public abstract long getCurrentRound();
}
