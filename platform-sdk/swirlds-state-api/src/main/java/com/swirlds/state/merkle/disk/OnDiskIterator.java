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

package com.swirlds.state.merkle.disk;

import static java.util.Objects.requireNonNull;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleIterator;
import com.swirlds.state.merkle.vmapsupport.OnDiskKey;
import com.swirlds.state.merkle.vmapsupport.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.merkle.VirtualLeafNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class OnDiskIterator<K, V> implements Iterator<K> {
    private final MerkleIterator<MerkleNode> itr;
    private K next = null;

    public OnDiskIterator(@NonNull final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap) {
        itr = requireNonNull(virtualMap).treeIterator();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean hasNext() {
        if (next != null) {
            return true;
        }
        while (itr.hasNext()) {
            final var merkleNode = itr.next();
            if (merkleNode instanceof VirtualLeafNode<?, ?> leaf) {
                final var k = leaf.getKey();
                if (k instanceof OnDiskKey<?> onDiskKey) {
                    this.next = (K) onDiskKey.getKey();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public K next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        final var k = next;
        next = null;
        return k;
    }
}
