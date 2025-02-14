// SPDX-License-Identifier: Apache-2.0
package com.swirlds.common.merkle.impl;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRoute;

/**
 * Implementing this interface allows for a class to implement the {@link MerkleInternal} interface using inheritance
 * by composition with minimal boilerplate.
 */
@ConstructableIgnored
public interface ComposedMerkleInternal extends MerkleInternal {

    /**
     * Get an object that implements the {@link PartialMerkleInternal} interface.
     * This object will be inherited by composition.
     *
     * @return an object which implements the {@link PartialMerkleInternal} interface.
     */
    PartialMerkleInternal getMerkleImplementation();

    /**
     * {@inheritDoc}
     */
    @Override
    default void setHash(final Hash hash) {
        getMerkleImplementation().setHash(hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default int getNumberOfChildren() {
        return getMerkleImplementation().getNumberOfChildren();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default <T extends MerkleNode> T getChild(final int index) {
        return getMerkleImplementation().getChild(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default void setChild(
            final int index, final MerkleNode child, final MerkleRoute childRoute, final boolean childMayBeImmutable) {

        getMerkleImplementation().setChild(index, child, childRoute, childMayBeImmutable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default Hash getHash() {
        return getMerkleImplementation().getHash();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default MerkleRoute getRoute() {
        return getMerkleImplementation().getRoute();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default void setRoute(final MerkleRoute route) {
        getMerkleImplementation().setRoute(route);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default void reserve() {
        getMerkleImplementation().reserve();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean tryReserve() {
        return getMerkleImplementation().tryReserve();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean release() {
        return getMerkleImplementation().release();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean isDestroyed() {
        return getMerkleImplementation().isDestroyed();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default int getReservationCount() {
        return getMerkleImplementation().getReservationCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean isImmutable() {
        return getMerkleImplementation().isImmutable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean isLeaf() {
        return getMerkleImplementation().isLeaf();
    }
}
