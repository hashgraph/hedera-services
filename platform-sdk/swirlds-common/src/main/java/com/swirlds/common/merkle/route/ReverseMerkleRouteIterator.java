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

package com.swirlds.common.merkle.route;

import com.swirlds.common.merkle.MerkleNode;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Iterate over a route in a merkle tree in reverse order (from leaf to root).
 */
public class ReverseMerkleRouteIterator implements Iterator<MerkleNode> {

    private final Iterator<MerkleNode> iterator;

    public ReverseMerkleRouteIterator(final MerkleNode root, final MerkleRoute routeData) {
        LinkedList<MerkleNode> nodes = new LinkedList<>();

        MerkleRouteIterator it = new MerkleRouteIterator(root, routeData);
        while (it.hasNext()) {
            nodes.addFirst(it.next());
        }

        iterator = nodes.iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public MerkleNode next() {
        return iterator.next();
    }
}
