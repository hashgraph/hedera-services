/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.merkle.iterators.MerkleIterationOrder.BREADTH_FIRST;
import static com.swirlds.virtualmap.test.fixtures.VirtualMapTestUtils.createMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRouteFactory;
import com.swirlds.common.merkle.route.MerkleRouteIterator;
import com.swirlds.common.merkle.route.ReverseMerkleRouteIterator;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.test.fixtures.VirtualTestBase;
import java.util.Iterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@SuppressWarnings("SpellCheckingInspection")
class VirtualMerkleNavigationTest extends VirtualTestBase {

    private VirtualMap vm;
    private VirtualRootNode virtualRoot;
    private VirtualInternalNode left;
    private VirtualInternalNode right;
    private VirtualInternalNode leftLeft;
    private VirtualInternalNode leftRight;
    private VirtualInternalNode rightLeft;
    private VirtualLeafNode a;
    private VirtualLeafNode b;
    private VirtualLeafNode c;
    private VirtualLeafNode d;
    private VirtualLeafNode e;
    private VirtualLeafNode f;
    private VirtualLeafNode g;
    private TestInternal treeRoot;
    private TestInternal tl;
    private TestInternal tr;
    private TestInternal tll;
    private TestInternal tlr;
    private TestInternal trl;
    private TestLeaf trr;
    private TestLeaf tlll;
    private TestLeaf tllr;
    private TestLeaf tlrl;
    private TestLeaf trll;
    private TestLeaf trlr;

    @BeforeEach
    public void setup() {
        vm = createMap();
        vm.put(A_KEY, APPLE);
        vm.put(B_KEY, BANANA);
        vm.put(C_KEY, CHERRY);
        vm.put(D_KEY, DATE);
        vm.put(E_KEY, EGGPLANT);
        vm.put(F_KEY, FIG);
        vm.put(G_KEY, GRAPE);

        virtualRoot = vm.getChild(1);
        assert virtualRoot != null;

        left = virtualRoot.getChild(0);
        right = virtualRoot.getChild(1);
        assert left != null;
        assert right != null;

        leftLeft = left.getChild(0);
        leftRight = left.getChild(1);
        rightLeft = right.getChild(0);
        assert leftLeft != null;
        assert leftRight != null;
        assert rightLeft != null;

        d = right.getChild(1);
        a = leftLeft.getChild(0);
        e = leftLeft.getChild(1);
        c = leftRight.getChild(0);
        f = leftRight.getChild(1);
        b = rightLeft.getChild(0);
        g = rightLeft.getChild(1);

        treeRoot = new TestInternal("TreeRoot");
        tl = new TestInternal("InternalLeft");
        tr = new TestInternal("InternalRight");
        tll = new TestInternal("InternalLeftLeft");
        tlr = new TestInternal("InternalLeftRight");
        trl = new TestInternal("InternalRightLeft");
        trr = new TestLeaf("rightRight");
        tlll = new TestLeaf("leftLeftLeft");
        tllr = new TestLeaf("leftLeftRight");
        tlrl = new TestLeaf("leftRightLeft");
        trll = new TestLeaf("rightLeftLeft");
        trlr = new TestLeaf("rightLeftRight");

        treeRoot.setLeft(tl);
        treeRoot.setRight(tr);

        tl.setLeft(tll);
        tl.setRight(tlr);

        tr.setLeft(trl);
        tr.setRight(trr);

        tll.setLeft(tlll);
        tll.setRight(tllr);

        tlr.setLeft(tlrl);
        tlr.setRight(vm);

        trl.setLeft(trll);
        trl.setRight(trlr);
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("TreeNav")})
    @DisplayName("Verify that the tree is navigable using 'getChild'")
    void treeIsNavigableByGetChild() {
        // Verify that all the internal nodes are where they should be
        assertEquals(APPLE, a.getValue(), "Wrong value");
        assertEquals(BANANA, b.getValue(), "Wrong value");
        assertEquals(CHERRY, c.getValue(), "Wrong value");
        assertEquals(DATE, d.getValue(), "Wrong value");
        assertEquals(EGGPLANT, e.getValue(), "Wrong " + "value");
        assertEquals(FIG, f.getValue(), "Wrong value");
        assertEquals(GRAPE, g.getValue(), "Wrong value");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("TreeNav")})
    @DisplayName("Verify that the tree is navigable using a breadth first iterator")
    void treeIsNavigableByBreadthFirstIterator() {
        final Iterator<MerkleNode> itr = treeRoot.treeIterator().setOrder(BREADTH_FIRST);
        assertSame(treeRoot, itr.next(), "Wrong value");
        assertSame(tl, itr.next(), "Wrong value");
        assertSame(tr, itr.next(), "Wrong value");
        assertSame(tll, itr.next(), "Wrong value");
        assertSame(tlr, itr.next(), "Wrong value");
        assertSame(trl, itr.next(), "Wrong value");
        assertSame(trr, itr.next(), "Wrong value");
        assertSame(tlll, itr.next(), "Wrong value");
        assertSame(tllr, itr.next(), "Wrong value");
        assertSame(tlrl, itr.next(), "Wrong value");
        assertSame(vm, itr.next(), "Wrong value");
        assertSame(trll, itr.next(), "Wrong value");
        assertSame(trlr, itr.next(), "Wrong value");
        itr.next(); // skip over the map metadata node
        assertEquals(virtualRoot, itr.next(), "Wrong value");
        assertEquals(left, itr.next(), "Wrong value");
        assertEquals(right, itr.next(), "Wrong value");
        assertEquals(leftLeft, itr.next(), "Wrong value");
        assertEquals(leftRight, itr.next(), "Wrong value");
        assertEquals(rightLeft, itr.next(), "Wrong value");
        assertEquals(d, itr.next(), "Wrong value");
        assertEquals(a, itr.next(), "Wrong value");
        assertEquals(e, itr.next(), "Wrong value");
        assertEquals(c, itr.next(), "Wrong value");
        assertEquals(f, itr.next(), "Wrong value");
        assertEquals(b, itr.next(), "Wrong value");
        assertEquals(g, itr.next(), "Wrong value");
        assertFalse(itr.hasNext(), "Expected iteration to have ended");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("TreeNav")})
    @DisplayName("Verify that the tree is navigable using a depth first iterator")
    void treeIsNavigableByDepthFirstIterator() {
        final Iterator<MerkleNode> itr = treeRoot.treeIterator();
        assertSame(tlll, itr.next(), "Wrong value");
        assertSame(tllr, itr.next(), "Wrong value");
        assertSame(tll, itr.next(), "Wrong value");
        assertSame(tlrl, itr.next(), "Wrong value");
        itr.next(); // skip over the map metadata node
        assertEquals(a, itr.next(), "Wrong value");
        assertEquals(e, itr.next(), "Wrong value");
        assertEquals(leftLeft, itr.next(), "Wrong value");
        assertEquals(c, itr.next(), "Wrong value");
        assertEquals(f, itr.next(), "Wrong value");
        assertEquals(leftRight, itr.next(), "Wrong value");
        assertEquals(left, itr.next(), "Wrong value");
        assertEquals(b, itr.next(), "Wrong value");
        assertEquals(g, itr.next(), "Wrong value");
        assertEquals(rightLeft, itr.next(), "Wrong value");
        assertEquals(d, itr.next(), "Wrong value");
        assertEquals(right, itr.next(), "Wrong value");
        assertEquals(virtualRoot, itr.next(), "Wrong value");
        assertSame(vm, itr.next(), "Wrong value");
        assertSame(tlr, itr.next(), "Wrong value");
        assertSame(tl, itr.next(), "Wrong value");
        assertSame(trll, itr.next(), "Wrong value");
        assertSame(trlr, itr.next(), "Wrong value");
        assertSame(trl, itr.next(), "Wrong value");
        assertSame(trr, itr.next(), "Wrong value");
        assertSame(tr, itr.next(), "Wrong value");
        assertSame(treeRoot, itr.next(), "Wrong value");
        assertFalse(itr.hasNext(), "Expected iteration to have ended");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("TreeNav")})
    @DisplayName("Verify that the tree is navigable using a merkle route iterator")
    void treeIsNavigableByMerkleRouteIterator() {
        // Should land me on Cherry
        final MerkleRouteIterator itr = new MerkleRouteIterator(
                treeRoot,
                MerkleRouteFactory.getEmptyRoute()
                        .extendRoute(0)
                        .extendRoute(1)
                        .extendRoute(1)
                        .extendRoute(1)
                        .extendRoute(0)
                        .extendRoute(1)
                        .extendRoute(0));

        assertSame(treeRoot, itr.next(), "Wrong value");
        assertSame(tl, itr.next(), "Wrong value");
        assertSame(tlr, itr.next(), "Wrong value");
        assertSame(vm, itr.next(), "Wrong value");
        assertEquals(virtualRoot, itr.next(), "Wrong value");
        assertEquals(left, itr.next(), "Wrong value");
        assertEquals(leftRight, itr.next(), "Wrong value");
        assertEquals(c, itr.next(), "Wrong value");
        assertFalse(itr.hasNext(), "Expected iteration to have ended");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("TreeNav")})
    @DisplayName("Verify that the tree is navigable using a reverse merkle route iterator")
    void treeIsNavigableByReverseMerkleRouteIterator() {
        // Should land me on Cherry
        final ReverseMerkleRouteIterator itr = new ReverseMerkleRouteIterator(
                treeRoot,
                MerkleRouteFactory.getEmptyRoute()
                        .extendRoute(0)
                        .extendRoute(1)
                        .extendRoute(1)
                        .extendRoute(1)
                        .extendRoute(0)
                        .extendRoute(1)
                        .extendRoute(0));

        assertEquals(c, itr.next(), "Wrong value");
        assertEquals(leftRight, itr.next(), "Wrong value");
        assertEquals(left, itr.next(), "Wrong value");
        assertEquals(virtualRoot, itr.next(), "Wrong value");
        assertSame(vm, itr.next(), "Wrong value");
        assertSame(tlr, itr.next(), "Wrong value");
        assertSame(tl, itr.next(), "Wrong value");
        assertSame(treeRoot, itr.next(), "Wrong value");
        assertFalse(itr.hasNext(), "Expected iteration to have ended");
    }
}
