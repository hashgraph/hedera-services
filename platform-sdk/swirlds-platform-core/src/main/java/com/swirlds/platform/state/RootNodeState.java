package com.swirlds.platform.state;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.platform.system.SwirldState;

/**
 * This interface represents the root node of Hedera Merkle tree.
 */
public interface RootNodeState extends MerkleInternal {
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
    RootNodeState copy();
}
