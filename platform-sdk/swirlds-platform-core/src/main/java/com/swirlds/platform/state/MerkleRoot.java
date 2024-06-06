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

/**
 * This interface represents the root node of Hedera Merkle tree.
 */
public interface MerkleRoot extends MerkleInternal {
    /**
     * Get the application state.
     *
     * @return the application state
     */
    SwirldState getSwirldState();

    /**
     * Get the platform state.
     *
     * @return the platform state
     */
    PlatformState getPlatformState();
    /**
     * Set the platform state.
     *
     * @param platformState the platform state
     */
    void setPlatformState(PlatformState platformState);

    /**
     * Generate a string that describes this state.
     *
     * @param hashDepth the depth of the tree to visit and print
     */
    String getInfoString(int hashDepth);

    /** {@inheritDoc} */
    MerkleRoot copy();
}
