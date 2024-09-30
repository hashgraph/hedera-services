/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.platform.system.SwirldState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This interface represents the root node of the Merkle tree.
 */
public interface MerkleRoot extends MerkleInternal {
    /**
     * Get the application state.
     *
     * @return the application state
     */
    @NonNull
    SwirldState getSwirldState();

    /**
     * This method makes sure that the platform state is initialized.
     * If it's already initialized, it does nothing.
     */
    void initPlatformState();

    /**
     * Get readable platform state.
     * Works on both - mutable and immutable {@link MerkleRoot} and, therefore, this method should be preferred.
     *
     * @return immutable platform state
     */
    @NonNull
    PlatformStateAccessor getReadablePlatformState();

    /**
     * Get writable platform state. Works only on mutable {@link MerkleRoot}.
     * Call this method only if you need to modify the platform state.
     *
     * @return mutable platform state
     */
    @NonNull
    PlatformStateModifier getWritablePlatformState();

    /**
     * Set the platform state.
     *
     * @param platformState the platform state
     */
    void updatePlatformState(@NonNull final PlatformStateModifier platformState);

    /**
     * Generate a string that describes this state.
     *
     * @param hashDepth the depth of the tree to visit and print
     */
    @NonNull
    String getInfoString(final int hashDepth);

    /** {@inheritDoc} */
    @NonNull
    MerkleRoot copy();
}
