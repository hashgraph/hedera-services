/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.merkle;

import static com.swirlds.common.merkle.utility.MerkleUtils.invalidateTree;
import static com.swirlds.common.merkle.utility.MerkleUtils.rehashTree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.test.merkle.util.MerkleTestUtils;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Merkle Rehash Tests")
class MerkleRehashTests {

    /**
     * If the root of the tree is an internal node, add some self hashing nodes that "explode" if rehashed.
     */
    private void addSelfHashingNodes(final MerkleNode root, final Cryptography cryptography) {
        if (!root.isLeaf()) {
            final MerkleInternal internal = root.asInternal();
            final int childCount = internal.getNumberOfChildren();

            internal.setChild(childCount, new DummySelfHashingLeaf(cryptography));

            final MerkleInternal subtree = new DummySelfHashingInternal(cryptography);
            subtree.setChild(0, new DummySelfHashingLeaf(cryptography));
            subtree.setChild(1, new DummySelfHashingLeaf(cryptography));

            internal.setChild(childCount + 1, subtree);
        }
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Invalidate Behavior")
    void invalidateBehavior() {

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        for (final MerkleNode root : MerkleTestUtils.buildTreeList()) {
            if (root == null) {
                continue;
            }

            addSelfHashingNodes(root, platformContext.getCryptography());

            root.forEachNode((final MerkleNode node) -> {
                if (node.isSelfHashing()) {
                    assertEquals(
                            platformContext.getCryptography().getNullHash(),
                            node.getHash(),
                            "dummy node should have null hash");
                } else {
                    assertNull(node.getHash(), "node should have a null hash");
                }
            });

            MerkleCryptoFactory.getInstance().digestTreeSync(root);

            root.forEachNode((final MerkleNode node) -> {
                assertNotNull(node.getHash(), "node should not have a null hash");
            });

            invalidateTree(root);

            root.forEachNode((final MerkleNode node) -> {
                if (node.isSelfHashing()) {
                    assertEquals(
                            platformContext.getCryptography().getNullHash(),
                            node.getHash(),
                            "dummy node should have null hash");
                } else {
                    assertNull(node.getHash(), "node should have a null hash");
                }
            });
        }
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Rehash Behavior")
    void rehashBehavior() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        for (final MerkleNode root : MerkleTestUtils.buildTreeList()) {
            if (root == null) {
                continue;
            }

            addSelfHashingNodes(root, platformContext.getCryptography());

            root.forEachNode((final MerkleNode node) -> {
                if (node.isSelfHashing()) {
                    assertEquals(
                            platformContext.getCryptography().getNullHash(),
                            node.getHash(),
                            "dummy node should have null hash");
                } else {
                    assertNull(node.getHash(), "node should have a null hash");
                }
            });

            MerkleCryptoFactory.getInstance().digestTreeSync(root);

            final Map<MerkleRoute, Hash> hashes = new HashMap<>();

            root.forEachNode((final MerkleNode node) -> {
                assertNotNull(node.getHash(), "node should not have a null hash");
                hashes.put(node.getRoute(), node.getHash());
            });

            rehashTree(root);

            root.forEachNode((final MerkleNode node) -> {
                assertNotNull(node.getHash(), "node should not have a null hash");
                assertEquals(hashes.get(node.getRoute()), node.getHash(), "node should have the same hash");
                if (!node.isSelfHashing()) {
                    assertNotSame(hashes.get(node.getRoute()), node.getHash(), "hash should be a different object");
                }
            });
        }
    }

    private static class DummySelfHashingLeaf extends PartialMerkleLeaf implements MerkleLeaf {

        private final Cryptography cryptography;

        private DummySelfHashingLeaf(final Cryptography cryptography) {
            this.cryptography = CommonUtils.throwArgNull(cryptography, "cryptography");
        }

        @Override
        public long getClassId() {
            // unique ID just in case constructable registry discovers this object
            return 0xeca90add22ad0775L;
        }

        @Override
        public void serialize(final SerializableDataOutputStream out) throws IOException {}

        @Override
        public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {}

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public DummySelfHashingLeaf copy() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void invalidateHash() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Hash getHash() {
            return cryptography.getNullHash();
        }

        @Override
        public void setHash(final Hash hash) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean isSelfHashing() {
            return true;
        }
    }

    private static class DummySelfHashingInternal extends PartialNaryMerkleInternal implements MerkleInternal {

        private final Cryptography cryptography;

        private DummySelfHashingInternal(final Cryptography cryptography) {
            this.cryptography = CommonUtils.throwArgNull(cryptography, "cryptography");
        }

        @Override
        public long getClassId() {
            // unique ID just in case constructable registry discovers this object
            return 0xcbac5958c3d04e5bL;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public DummySelfHashingInternal copy() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void invalidateHash() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Hash getHash() {
            return cryptography.getNullHash();
        }

        @Override
        public void setHash(final Hash hash) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean isSelfHashing() {
            return true;
        }
    }
}
