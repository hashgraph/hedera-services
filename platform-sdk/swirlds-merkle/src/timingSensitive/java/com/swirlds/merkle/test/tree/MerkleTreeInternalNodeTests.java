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

package com.swirlds.merkle.test.tree;

import static com.swirlds.merkle.test.tree.MerkleBinaryTreeTests.insertIntoTree;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.base.state.MutabilityException;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.exceptions.ReferenceCountException;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.route.MerkleRouteIterator;
import com.swirlds.common.test.fixtures.dummy.Key;
import com.swirlds.common.test.fixtures.dummy.Value;
import com.swirlds.common.test.fixtures.junit.tags.TestComponentTags;
import com.swirlds.merkle.tree.MerkleBinaryTree;
import com.swirlds.merkle.tree.MerkleTreeInternalNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("MerkleTree Internal Node Tests")
class MerkleTreeInternalNodeTests {

    /**
     * Utility method for finding a node's parent. O(log(n)).
     */
    private MerkleInternal getParent(final MerkleNode root, final MerkleRoute route) {
        final MerkleRouteIterator iterator = new MerkleRouteIterator(root, route);
        final int depth = route.size();

        int currentDepth = 0;
        MerkleNode parent = root;
        while (currentDepth < depth - 1) {
            currentDepth++;
            parent = iterator.next();
        }

        return parent.asInternal();
    }

    @Test
    @Tag(TestComponentTags.MMAP)
    @DisplayName("NullifyIntervalNodeTest")
    void copyIsMutable() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.merkle.map.*");

        final MerkleBinaryTree<Value> tree = new MerkleBinaryTree<>();

        insertIntoTree(0, 4, tree, MerkleBinaryTreeTests::updateCache);

        final Key key01 = new Key(new long[] {0, 0, 0});
        final Value value01 = tree.findValue(v -> v.getKey().equals(key01));
        final MerkleTreeInternalNode parent = (MerkleTreeInternalNode) getParent(tree, value01.getRoute());

        final MerkleTreeInternalNode mutableParent = parent.copy();

        assertNotSame(mutableParent, parent, "copy should not be the same object");

        assertThrows(MutabilityException.class, () -> parent.setLeft(null), "expected this method to fail");

        mutableParent.setLeft(null);

        assertNull(mutableParent.getLeft(), "Left child was set to null");
    }

    @Test
    @Tag(TestComponentTags.MMAP)
    void copyThrowsIfDeletedTest() {
        final MerkleTreeInternalNode fcmNode = new MerkleTreeInternalNode();
        fcmNode.release();

        final Exception exception =
                assertThrows(ReferenceCountException.class, fcmNode::copy, "expected this method to fail");
    }
}
