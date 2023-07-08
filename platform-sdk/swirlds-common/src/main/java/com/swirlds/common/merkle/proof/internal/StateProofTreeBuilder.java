/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.proof.internal;

import static com.swirlds.common.utility.ByteUtils.intToByteArray;
import static com.swirlds.common.utility.ByteUtils.longToByteArray;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRoute;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Logic for building a state proof tree.
 */
public final class StateProofTreeBuilder {

    // TODO make these methods package private and unit test them individually

    private StateProofTreeBuilder() {}

    /**
     * Check if a merkle node is an ancestor of at least one of the payloads. Merkle nodes are considered to be
     * ancestors to themselves.
     *
     * @param potentialAncestor the potential ancestor
     * @param payloads          the payloads
     * @return true if the potential ancestor is an ancestor of at least one payload, otherwise false
     */
    private static boolean isAncestorOfPayload(
            @NonNull final MerkleNode potentialAncestor, @NonNull final List<MerkleLeaf> payloads) {

        for (final MerkleLeaf payload : payloads) {
            if (payload.getRoute().isAncestorOf(potentialAncestor.getRoute())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Given a merkle root and a list of payloads, extract a list of merkle nodes that are ancestors of at least one of
     * the payloads. Nodes are returned in post-ordered depth first traversal order.
     *
     * @param merkleRoot the merkle root
     * @param payloads   the payloads
     * @return a list of merkle nodes that are ancestors of at least one of the payloads
     * @throws IllegalStateException if the payloads and the merkle tree are inconsistent with each other
     */
    private static List<MerkleNode> getMerkleNodesForStateProofTree(
            @NonNull final MerkleNode merkleRoot, @NonNull final List<MerkleLeaf> payloads) {

        final List<MerkleNode> nodes = new ArrayList<>();
        merkleRoot
                .treeIterator()
                .ignoreNull(true)
                .setFilter(node -> isAncestorOfPayload(node, payloads))
                .setDescendantFilter(node -> isAncestorOfPayload(node, payloads))
                .forEachRemaining(nodes::add);

        return nodes;
    }

    /**
     * Construct a set of merkle routes from a list of merkle nodes.
     *
     * @param nodes the nodes to extract the routes from
     * @return a set of all merkle routes in the list of nodes
     */
    private static Set<MerkleRoute> getMerkleRouteSet(@NonNull final List<MerkleNode> nodes) {
        final Set<MerkleRoute> routes = new HashSet<>();
        for (final MerkleNode node : nodes) {
            routes.add(node.getRoute());
        }
        return routes;
    }

    /**
     * Sanity check the payloads against the node list.
     *
     * @param nodes      the list of nodes to include in the state proof tree
     * @param nodeRoutes the set of routes of the nodes in the node list
     * @param payloads   the payloads to include in the state proof
     */
    private static void validatePayloads(
            @NonNull final List<MerkleNode> nodes,
            @NonNull final Set<MerkleRoute> nodeRoutes,
            @NonNull final List<MerkleLeaf> payloads) {

        // Every payload should appear in the node list.
        for (final MerkleLeaf leaf : payloads) {
            if (!nodeRoutes.contains(leaf.getRoute())) {
                throw new IllegalStateException("Payloads are inconsistent with merkle tree. Payloads contain "
                        + "leaf " + leaf + " which is not in the merkle tree.");
            }
        }

        // The number of payloads should match exactly the number of leaves in the node list.
        int leafCount = 0;
        for (final MerkleNode node : nodes) {
            if (node.isLeaf()) {
                leafCount++;
            }
        }
        if (leafCount != payloads.size()) {
            throw new IllegalStateException("Payloads are inconsistent with merkle tree. Payloads count is "
                    + payloads.size() + " but the node list contains " + leafCount + " leaves.");
        }
    }

    /**
     * Build an internal node in the state proof tree.
     *
     * @param cryptography provides cryptographic primitives
     * @param node       the node to build the state proof node for
     * @param nodeRoutes the set of routes of all nodes to be included in the state proof tree
     * @param children   a queue containing state proof nodes waiting to be added to their parents
     * @return the state proof node
     */
    @NonNull
    private static StateProofNode buildStateProofInternalNode(
            @NonNull final Cryptography cryptography,
            @NonNull final MerkleInternal node,
            @NonNull final Set<MerkleRoute> nodeRoutes,
            @NonNull final Queue<StateProofNode> children) {

        final List<StateProofNode> selfChildren = new ArrayList<>();

        final List<byte[]> byteSegments = new ArrayList<>();

        // First, the class ID and version are append.
        byteSegments.add(longToByteArray(node.getClassId()));
        byteSegments.add(intToByteArray(node.getVersion()));

        // Then, the hashes of the children are appended.
        for (int childIndex = 0; childIndex < node.getNumberOfChildren(); childIndex++) {

            final MerkleNode child = node.getChild(childIndex);

            if (child == null) {
                // If the child is null, append the null hash.
                byteSegments.add(cryptography.getNullHash().getValue());
            } else if (nodeRoutes.contains(child.getRoute())) {
                // If the child is in the node list, then we add its state proof node.

                if (!byteSegments.isEmpty()) {
                    // If we have accumulated byte segments, wrap them in an opaque node.
                    selfChildren.add(new StateProofOpaqueNode(byteSegments));
                    byteSegments.clear();
                }

                // The child we need is guaranteed to be the next in the child queue.
                selfChildren.add(children.remove());
            } else {
                // If the child is not in the node list, we append its hash.
                byteSegments.add(child.getHash().getValue());
            }
        }

        if (!byteSegments.isEmpty()) {
            // If we have remaining byte segments, wrap them in an opaque node.
            selfChildren.add(new StateProofOpaqueNode(byteSegments));
        }

        return new StateProofInternalNode(selfChildren);
    }

    /**
     * Build the next state proof node and add it to the child queue.
     *
     * @param cryptography provides cryptographic primitives
     * @param node       the node to build the state proof node for
     * @param nodeRoutes the set of routes of all nodes to be included in the state proof tree
     * @param children   a queue containing state proof nodes waiting to be added to their parents
     */
    private static void buildStateProofNode(
            @NonNull final Cryptography cryptography,
            @NonNull final MerkleNode node,
            @NonNull final Set<MerkleRoute> nodeRoutes,
            @NonNull final Queue<StateProofNode> children) {
        if (node.isLeaf()) {
            children.add(new StateProofPayload(node.asLeaf()));
        } else {
            children.add(buildStateProofInternalNode(cryptography, node.asInternal(), nodeRoutes, children));
        }
    }

    /**
     * Build the state proof tree from a merkle tree.
     *
     * @param cryptography provides cryptographic primitives
     * @param merkleRoot the root of the merkle tree
     * @param payloads   the payloads to build the state proof tree on
     * @return the root of the state proof tree
     */
    @NonNull
    public static StateProofNode buildStateProofTree(
            @NonNull final Cryptography cryptography,
            @NonNull final MerkleNode merkleRoot,
            @NonNull final List<MerkleLeaf> payloads) {

        final List<MerkleNode> nodes = getMerkleNodesForStateProofTree(merkleRoot, payloads);
        final Set<MerkleRoute> nodeRoutes = getMerkleRouteSet(nodes);
        validatePayloads(nodes, nodeRoutes, payloads);

        final Queue<StateProofNode> children = new LinkedList<>();
        for (final MerkleNode node : nodes) {
            buildStateProofNode(cryptography, node, nodeRoutes, children);
        }

        // When we are done, the queue should contain just the root node of the state proof tree.
        if (children.size() != 1) {
            throw new IllegalStateException("State proof tree construction failed. Expected to find just the root node "
                    + "but found " + children.size() + " nodes.");
        }

        return children.remove();
    }
}
