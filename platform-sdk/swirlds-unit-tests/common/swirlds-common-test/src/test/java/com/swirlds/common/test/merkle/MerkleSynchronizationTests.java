/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.merkle.copy.MerkleInitialize.initializeTreeAfterCopy;
import static com.swirlds.test.framework.ResourceLoader.loadLog4jContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.copy.MerkleCopy;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettingsFactory;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.utility.MerkleUtils;
import com.swirlds.common.test.merkle.dummy.DummyCustomReconnectRoot;
import com.swirlds.common.test.merkle.dummy.DummyMerkleInternal;
import com.swirlds.common.test.merkle.dummy.DummyMerkleLeaf;
import com.swirlds.common.test.merkle.dummy.DummyMerkleNode;
import com.swirlds.common.test.merkle.util.MerkleTestUtils;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.TestTypeTags;
import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("Merkle Synchronization Tests")
public class MerkleSynchronizationTests {

    @BeforeAll
    public static void startup() throws ConstructableRegistryException, FileNotFoundException {
        loadLog4jContext();
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.common");

        ReconnectSettingsFactory.configure(new ReconnectSettings() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public int getReconnectWindowSeconds() {
                return -1;
            }

            @Override
            public double getFallenBehindThreshold() {
                return 0.5;
            }

            @Override
            public int getAsyncStreamTimeoutMilliseconds() {
                // This is lower than the default, helps test that is supposed to fail to finish faster.
                return 500;
            }

            @Override
            public int getAsyncOutputStreamFlushMilliseconds() {
                return 100;
            }

            @Override
            public int getAsyncStreamBufferSize() {
                return 10_000;
            }

            @Override
            public int getMaxAckDelayMilliseconds() {
                return 10;
            }

            @Override
            public int getMaximumReconnectFailuresBeforeShutdown() {
                return 10;
            }

            @Override
            public Duration getMinimumTimeBetweenReconnects() {
                return Duration.ofMinutes(10);
            }
        });
    }

    /**
     * For each n^2 combination of trees, sync one tree to the other.
     */
    protected void testSynchronization(Supplier<List<MerkleNode>> treeListBuilder) throws Exception {
        final List<MerkleNode> listI = treeListBuilder.get();
        final List<MerkleNode> listJ = treeListBuilder.get();

        // Setup initial reference count for roots of tree
        listI.forEach((final MerkleNode root) -> {
            if (root != null) {
                root.reserve();
            }
        });
        listJ.forEach((final MerkleNode root) -> {
            if (root != null) {
                root.reserve();
            }
        });

        for (final MerkleNode nodeI : listI) {
            for (final MerkleNode nodeJ : listJ) {
                final MerkleNode result = MerkleTestUtils.hashAndTestSynchronization(nodeI, nodeJ);
                if (result != null && result != nodeI) {
                    result.release();
                }
            }
        }

        // Verify that none of the original trees have not been modified
        final List<MerkleNode> cleanList = treeListBuilder.get();
        for (int i = 0; i < cleanList.size(); i++) {
            assertTrue(
                    MerkleTestUtils.areTreesEqual(cleanList.get(i), listI.get(i)),
                    "ListI does not match the original tree");
            assertTrue(
                    MerkleTestUtils.areTreesEqual(cleanList.get(i), listJ.get(i)),
                    "ListJ does not match the original tree");
        }

        // Clean up reference counts
        listI.forEach((final MerkleNode root) -> {
            if (root != null) {
                root.release();
                root.forEachNode((final MerkleNode node) -> {
                    if (node != null) {
                        assertTrue(node.isDestroyed(), "all nodes should be destroyed");
                    }
                });
            }
        });
        listJ.forEach((final MerkleNode root) -> {
            if (root != null) {
                root.release();
                root.forEachNode((final MerkleNode node) -> {
                    if (node != null) {
                        assertTrue(node.isDestroyed(), "all nodes should be destroyed");
                    }
                });
            }
        });
    }

    /**
     * Generate a random tree and put a DummyCustomReconnectViewRoot node at the root.
     */
    private MerkleNode buildRandomTreeWithCustomRoot(final int seed, final int depth) {
        final MerkleInternal root = new DummyCustomReconnectRoot();
        root.rebuild();
        final MerkleNode randomTree = MerkleTestUtils.generateRandomBalancedTree(seed, depth, 2, 20, 0);
        MerkleCopy.adoptChildren(randomTree.asInternal(), root);
        randomTree.release();
        return root;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Synchronization Tests")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void synchronizationTests() throws Exception {

        final long seed = new Random().nextLong();
        System.out.println("seed = " + seed);

        final Supplier<List<MerkleNode>> builder = () -> {
            final Random random = new Random(seed);

            final List<MerkleNode> roots = new ArrayList<>(MerkleTestUtils.buildTreeList());

            // one custom view subtree
            final MerkleInternal tree0 = MerkleTestUtils.buildLessSimpleTreeExtended();
            tree0.setChild(3, buildRandomTreeWithCustomRoot(random.nextInt(), 2));
            roots.add(tree0);

            // one custom view subtree in a different location
            final MerkleInternal tree1 = MerkleTestUtils.buildLessSimpleTreeExtended();
            tree1.getChild(2)
                    .asInternal()
                    .getChild(1)
                    .asInternal()
                    .getChild(0)
                    .asInternal()
                    .setChild(2, buildRandomTreeWithCustomRoot(random.nextInt(), 3));
            roots.add(tree1);

            // two custom view subtrees
            final MerkleInternal tree2 = MerkleTestUtils.buildLessSimpleTreeExtended();
            tree2.setChild(3, buildRandomTreeWithCustomRoot(random.nextInt(), 4));
            tree2.getChild(2)
                    .asInternal()
                    .getChild(1)
                    .asInternal()
                    .getChild(0)
                    .asInternal()
                    .setChild(2, buildRandomTreeWithCustomRoot(random.nextInt(), 5));
            roots.add(tree2);

            return roots;
        };

        testSynchronization(builder);
    }

    /**
     * *              root
     * *            / |  \ \
     * *           A  I0 B I1
     * *                  / \
     * *                 C  I2
     */
    protected DummyMerkleInternal buildTreeForVerifyResultIsATree() {
        final DummyMerkleLeaf A = new DummyMerkleLeaf("A");
        final DummyMerkleInternal I0 = new DummyMerkleInternal("I0");
        final DummyMerkleLeaf B = new DummyMerkleLeaf("B");
        final DummyMerkleLeaf C = new DummyMerkleLeaf("C");
        final DummyMerkleInternal I2 = new DummyMerkleInternal("I2");
        final DummyMerkleInternal I1 = new DummyMerkleInternal("I1");
        I1.setChild(0, C);
        I1.setChild(1, I2);
        final DummyMerkleInternal root = new DummyMerkleInternal("root");
        root.setChild(0, A);
        root.setChild(1, I0);
        root.setChild(2, B);
        root.setChild(3, I1);
        initializeTreeAfterCopy(root);

        return root;
    }

    /**
     * There was once a bug where the resulting merkle tree returned was not a DAG and not a tree.
     * This test verifies that the observed bug is no longer present.
     *
     * Starting tree
     *
     * *                root
     * *              / |  \ \
     * *             A  I0 B I1
     * *                    / \
     * *                   C  I2
     *
     * Desired tree
     *
     * *                root
     * *              / |  \ \
     * *             A  I0 B I1
     * *                    / \
     * *                   D  I2
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Verify result is a tree")
    void verifyResultIsATree() throws Exception {
        final DummyMerkleInternal startingTree = buildTreeForVerifyResultIsATree();
        startingTree.reserve();

        final DummyMerkleInternal desiredTree = buildTreeForVerifyResultIsATree();
        desiredTree.reserve();
        ((DummyMerkleInternal) desiredTree.getChild(3)).setChild(0, new DummyMerkleLeaf("D"));

        MerkleTestUtils.hashAndTestSynchronization(startingTree, desiredTree);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Verify exception handling")
    void verifyExceptionHandling() {
        // Sending an un-hashed tree should result in an exception
        final DummyMerkleNode desiredTree = MerkleTestUtils.buildLessSimpleTree();
        desiredTree.reserve();
        assertThrows(
                MerkleSynchronizationException.class, () -> MerkleTestUtils.testSynchronization(null, desiredTree));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Send Invalid Tree")
    void sendInvalidTree() throws Exception {
        // Modify a leaf value without re-hashing.
        final DummyMerkleNode root1 = MerkleTestUtils.buildLessSimpleTree();
        root1.reserve();
        MerkleCryptoFactory.getInstance().digestTreeSync(root1);

        ((DummyMerkleLeaf) root1.asInternal().getChild(0)).setValue("this is not the hashed value");

        final MerkleNode newRoot1 = MerkleTestUtils.hashAndTestSynchronization(null, root1);
        final Hash resultingHash1 = newRoot1.getHash();
        assertNotEquals(root1.getHash(), resultingHash1, "we should not derive the same hash since data was changed");
        MerkleUtils.rehashTree(newRoot1);
        assertEquals(
                resultingHash1,
                newRoot1.getHash(),
                "hash reported should be a valid representation of data sent by teacher");

        // Modify an internal node without re-hashing.
        final DummyMerkleNode root2 = MerkleTestUtils.buildLessSimpleTree();
        root2.reserve();
        MerkleCryptoFactory.getInstance().digestTreeSync(root2);

        Hash oldHash = root2.getHash();
        root2.asInternal().setChild(3, null);
        root2.setHash(oldHash);

        final MerkleNode newRoot2 = MerkleTestUtils.hashAndTestSynchronization(null, root2);
        final Hash resultingHash2 = newRoot2.getHash();
        assertNotEquals(root2.getHash(), resultingHash2, "we should not derive the same hash since data was changed");
        MerkleUtils.rehashTree(newRoot2);
        assertEquals(
                resultingHash2,
                newRoot2.getHash(),
                "hash reported should be a valid representation of data sent by teacher");
    }
}
