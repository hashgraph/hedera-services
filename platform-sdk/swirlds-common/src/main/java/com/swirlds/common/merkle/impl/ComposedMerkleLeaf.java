/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.impl;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.route.MerkleRoute;

/**
 * Implementing this interface allows for a class to implement the {@link MerkleLeaf} interface using inheritance
 * by composition with minimal boilerplate.
 */
public interface ComposedMerkleLeaf extends MerkleLeaf {

    /**
     * Get an object that implements the {@link PartialMerkleLeaf} interface.
     * This object will be inherited by composition.
     *
     * @return an object which implements the {@link PartialMerkleLeaf} interface.
     */
    PartialMerkleLeaf getMerkleImplementation();

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
    default void setHash(final Hash hash) {
        getMerkleImplementation().setHash(hash);
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
    default boolean isLeaf() {
        return getMerkleImplementation().isLeaf();
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
}
