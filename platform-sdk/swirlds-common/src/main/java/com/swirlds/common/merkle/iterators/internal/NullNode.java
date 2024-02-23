/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.iterators.internal;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.route.MerkleRoute;
import java.io.IOException;

/**
 * This object is used by {@link com.swirlds.common.merkle.iterators.MerkleIterator MerkleIterator}
 * as a placeholder for null values. This object's sole purpose is to store a route associated with
 * the null leaf, and so most other methods intentionally throw unsupported operation exceptions.
 */
@ConstructableIgnored
public class NullNode implements MerkleLeaf {

    public static final long CLASS_ID = 0x654cf5401e13e7e1L;

    private final MerkleRoute route;

    /**
     * Create a placeholder for a null node.
     */
    public NullNode(final MerkleRoute route) {
        this.route = route;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reserve() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryReserve() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean release() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDestroyed() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getReservationCount() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash getHash() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHash(final Hash hash) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleRoute getRoute() {
        return route;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRoute(final MerkleRoute route) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleLeaf copy() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLeaf() {
        return true;
    }
}
