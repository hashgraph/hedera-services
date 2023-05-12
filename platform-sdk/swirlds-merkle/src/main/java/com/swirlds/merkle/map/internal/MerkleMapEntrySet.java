/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.map.internal;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.MerkleMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;

/**
 * An entry set view for {@link MerkleMap}
 *
 * @param <K> the type of the map's key
 * @param <V> the type of the map's value
 */
public class MerkleMapEntrySet<K, V extends MerkleNode & Keyed<K>> extends AbstractSet<Map.Entry<K, V>> {
    /**
     * The merkle map this entry set is a view of
     */
    private final MerkleMap<K, V> merkleMap;

    /**
     * Constructor
     *
     * @param merkleMap the merkle map the entry set is a view of
     */
    public MerkleMapEntrySet(final MerkleMap<K, V> merkleMap) {
        this.merkleMap = merkleMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return new MerkleMapEntrySetIterator<>(merkleMap.getIndex().entrySet().iterator());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return merkleMap.size();
    }

    /**
     * Equals isn't implemented, because it would be extremely inefficient
     *
     * @param o object to be compared for equality with this set
     * @throws UnsupportedOperationException if called
     */
    @Override
    public boolean equals(final Object o) {
        throw new UnsupportedOperationException();
    }

    /**
     * HashCode isn't implemented, because it would be extremely inefficient
     *
     * @throws UnsupportedOperationException if called
     */
    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }
}
