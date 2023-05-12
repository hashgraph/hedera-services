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

package com.swirlds.virtualmap.internal.merkle;

import static com.swirlds.virtualmap.VirtualMapTestUtils.createRoot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.test.merkle.dummy.DummyBinaryMerkleInternal;
import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import com.swirlds.virtualmap.VirtualTestBase;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

class VirtualInternalNodeTest extends VirtualTestBase {

    /**
     * We don't support the setChild method. This test makes sure exceptions are raised.
     */
    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("setChild is not a supported method")
    void setChildNotSupported() {
        final VirtualInternalRecord internalRecord = new VirtualInternalRecord(1, null);
        final VirtualRootNode<TestKey, TestValue> root = createRoot();
        final VirtualInternalNode<TestKey, TestValue> internalNode = new VirtualInternalNode<>(root, internalRecord);

        final DummyBinaryMerkleInternal child = new DummyBinaryMerkleInternal();
        assertThrows(
                UnsupportedOperationException.class,
                () -> internalNode.setChild(3, child),
                "setChild is not required on VirtualInternalNodes");

        assertThrows(
                UnsupportedOperationException.class,
                () -> internalNode.setChild(3, child, mock(MerkleRoute.class), false),
                "setChild is not required on VirtualInternalNodes");
    }

    /**
     * The copy method is not supported either.
     */
    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("copy method is not supported")
    void copyNotSupported() {
        final VirtualInternalRecord internalRecord = new VirtualInternalRecord(0, null);
        final VirtualRootNode<TestKey, TestValue> root = createRoot();
        final VirtualInternalNode<TestKey, TestValue> internalNode = new VirtualInternalNode<>(root, internalRecord);

        assertThrows(UnsupportedOperationException.class, internalNode::copy, "Copy is not supported");
    }

    /**
     * Tries to read the children of a root node with no children.
     */
    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("Getting a leaf outside leaf paths returns null")
    void getInvalidLeaf() {
        // This root node has no children -- no left, and no right. This is a valid use case
        // (and in fact, any internal node other than the root node will ALWAYS have both a
        // left and right child).
        final VirtualInternalRecord internalRecord = new VirtualInternalRecord(0, null);
        final VirtualRootNode<TestKey, TestValue> root = createRoot();
        final VirtualInternalNode<TestKey, TestValue> internalNode = new VirtualInternalNode<>(root, internalRecord);
        assertNull(internalNode.getChild(0), "No left child");
        assertNull(internalNode.getChild(1), "No right child");
    }

    /**
     * This is a strict binary tree, so only values 0 and 1 would ever work.
     */
    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("Getting a child that isn't 0 or 1")
    @SuppressWarnings("unchecked")
    void getInvalidChild() {
        // Standard test scenario. There are valid children of this internalRecord. I want this so
        // that I know that under normal circumstances the call would succeed, and is only *not*
        // returning a valid because it shouldn't.
        final VirtualInternalRecord internalRecord = new VirtualInternalRecord(3, null);
        final VirtualRootNode<TestKey, TestValue> root = createRoot();
        root.put(A_KEY, APPLE);
        root.put(B_KEY, BANANA);
        root.put(C_KEY, CHERRY);
        root.put(D_KEY, DATE);
        root.put(E_KEY, EGGPLANT);
        root.put(F_KEY, FIG);
        root.put(G_KEY, GRAPE);
        final VirtualInternalNode<TestKey, TestValue> internalNode = new VirtualInternalNode<>(root, internalRecord);
        final VirtualLeafNode<TestKey, TestValue> leftChild = internalNode.getChild(0);
        final VirtualLeafNode<TestKey, TestValue> rightChild = internalNode.getChild(1);
        assertNotNull(leftChild, "child should not be null");
        assertNotNull(rightChild, "child should not be null");
        assertEquals(
                A_KEY,
                ((VirtualLeafRecord<TestKey, TestValue>) leftChild.getVirtualRecord()).getKey(),
                "key should match original");
        assertEquals(
                E_KEY,
                ((VirtualLeafRecord<TestKey, TestValue>) rightChild.getVirtualRecord()).getKey(),
                "key should match original");
        assertNull(internalNode.getChild(2), "value should be null");
    }

    /**
     * Get a child that is on disk. For rigour, I will test getting all 7 of
     * my leaf nodes from disk.
     */
    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("Getting a child that is on disk")
    @SuppressWarnings("unchecked")
    void getValidChildOnDisk() throws IOException {
        final VirtualRootNode<TestKey, TestValue> root = createRoot();
        root.getState().setFirstLeafPath(6);
        root.getState().setLastLeafPath(12);
        root.getDataSource()
                .saveRecords(
                        6,
                        12,
                        Stream.empty(),
                        Stream.of(
                                        new VirtualLeafRecord<>(6, null, D_KEY, DATE),
                                        new VirtualLeafRecord<>(7, null, A_KEY, APPLE),
                                        new VirtualLeafRecord<>(8, null, E_KEY, EGGPLANT),
                                        new VirtualLeafRecord<>(9, null, C_KEY, CHERRY),
                                        new VirtualLeafRecord<>(10, null, F_KEY, FIG),
                                        new VirtualLeafRecord<>(11, null, G_KEY, GRAPE),
                                        new VirtualLeafRecord<>(12, null, B_KEY, BANANA))
                                .map(this::hash),
                        Stream.empty());

        VirtualInternalRecord internalRecord = new VirtualInternalRecord(2, null);
        VirtualInternalNode<TestKey, TestValue> internalNode = new VirtualInternalNode<>(root, internalRecord);
        VirtualLeafNode<TestKey, TestValue> rightChildLeaf = internalNode.getChild(1);
        assertNotNull(rightChildLeaf, "value should not be null");
        assertEquals(
                D_KEY,
                ((VirtualLeafRecord<TestKey, TestValue>) rightChildLeaf.getVirtualRecord()).getKey(),
                "key should match original");

        internalRecord = new VirtualInternalRecord(3, null);
        internalNode = new VirtualInternalNode<>(root, internalRecord);
        VirtualLeafNode<TestKey, TestValue> leftChildLeaf = internalNode.getChild(0);
        rightChildLeaf = internalNode.getChild(1);
        assertNotNull(leftChildLeaf, "value should not be null");
        assertNotNull(rightChildLeaf, "value should not be null");
        assertEquals(
                A_KEY,
                ((VirtualLeafRecord<TestKey, TestValue>) leftChildLeaf.getVirtualRecord()).getKey(),
                "key should match original");
        assertEquals(
                E_KEY,
                ((VirtualLeafRecord<TestKey, TestValue>) rightChildLeaf.getVirtualRecord()).getKey(),
                "key should match original");

        internalRecord = new VirtualInternalRecord(4, null);
        internalNode = new VirtualInternalNode<>(root, internalRecord);
        leftChildLeaf = internalNode.getChild(0);
        rightChildLeaf = internalNode.getChild(1);
        assertNotNull(leftChildLeaf, "value should not be null");
        assertNotNull(rightChildLeaf, "value should not be null");
        assertEquals(
                C_KEY,
                ((VirtualLeafRecord<TestKey, TestValue>) leftChildLeaf.getVirtualRecord()).getKey(),
                "key should match original");
        assertEquals(
                F_KEY,
                ((VirtualLeafRecord<TestKey, TestValue>) rightChildLeaf.getVirtualRecord()).getKey(),
                "key should match original");

        internalRecord = new VirtualInternalRecord(5, null);
        internalNode = new VirtualInternalNode<>(root, internalRecord);
        leftChildLeaf = internalNode.getChild(0);
        rightChildLeaf = internalNode.getChild(1);
        assertNotNull(leftChildLeaf, "value should not be null");
        assertNotNull(rightChildLeaf, "value should not be null");
        assertEquals(
                G_KEY,
                ((VirtualLeafRecord<TestKey, TestValue>) leftChildLeaf.getVirtualRecord()).getKey(),
                "key should match original");
        assertEquals(
                B_KEY,
                ((VirtualLeafRecord<TestKey, TestValue>) rightChildLeaf.getVirtualRecord()).getKey(),
                "key should match original");
    }

    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("Check version and class are valid")
    void getVersionAndClassId() {
        final VirtualInternalRecord internalRecord = new VirtualInternalRecord(0, null);
        final VirtualRootNode<TestKey, TestValue> root = createRoot();
        final VirtualInternalNode<TestKey, TestValue> internalNode = new VirtualInternalNode<>(root, internalRecord);
        assertEquals(0xaf2482557cfdb6bfL, internalNode.getClassId(), "Increases code coverage for getClassId");
        assertEquals(1, internalNode.getVersion(), "Increases code coverage for getVersion");
    }

    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("toString doesn't throw")
    void toStringTest() {
        // Honestly, I'm just juicing the code coverage
        final VirtualInternalRecord internalRecord = new VirtualInternalRecord(0, null);
        final VirtualRootNode<TestKey, TestValue> root = createRoot();
        final VirtualInternalNode<TestKey, TestValue> internalNode = new VirtualInternalNode<>(root, internalRecord);
        assertNotNull(internalNode.toString(), "value should not be null");
    }
}
