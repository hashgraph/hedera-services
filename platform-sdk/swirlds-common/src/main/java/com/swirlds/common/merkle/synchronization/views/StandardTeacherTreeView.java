/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.synchronization.views;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.internal.NodeToSend;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * A teaching tree view for a standard in memory merkle tree.
 */
public class StandardTeacherTreeView implements TeacherTreeView<NodeToSend> {

    private final Queue<NodeToSend> nodesToHandle;
    private final BlockingQueue<NodeToSend> expectedResponses;

    private final NodeToSend root;

    /**
     * Create a view for a standard merkle tree.
     *
     * @param root
     * 		the root of the tree
     */
    public StandardTeacherTreeView(final MerkleNode root) {
        this.root = new NodeToSend(root);

        nodesToHandle = new LinkedList<>();
        expectedResponses = new LinkedBlockingDeque<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeToSend getRoot() {
        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addToHandleQueue(final NodeToSend node) {
        nodesToHandle.add(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeToSend getNextNodeToHandle() {
        return nodesToHandle.remove();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean areThereNodesToHandle() {
        return !nodesToHandle.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeToSend getChildAndPrepareForQueryResponse(final NodeToSend parent, final int childIndex) {
        final NodeToSend child = new NodeToSend(parent.getNode().asInternal().getChild(childIndex));
        parent.registerChild(child);

        if (!expectedResponses.add(child)) {
            throw new MerkleSynchronizationException("unable to add expected response to queue");
        }

        return child;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeToSend getNodeForNextResponse() {
        return expectedResponses.remove();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResponseExpected() {
        return !expectedResponses.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerResponseForNode(final NodeToSend node, final boolean learnerHasNode) {
        node.registerResponse(learnerHasNode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasLearnerConfirmedFor(final NodeToSend node) {
        node.waitForResponse();
        return node.getResponseStatus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInternal(final NodeToSend node, final boolean isOriginal) {
        // This implementation can safely ignore "isOriginal"
        final MerkleNode merkleNode = node.getNode();
        return merkleNode != null && !merkleNode.isLeaf();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId(final NodeToSend node) {
        final MerkleNode merkleNode = node.getNode();

        if (merkleNode == null) {
            throw new MerkleSynchronizationException("null has no class ID");
        }

        return merkleNode.getClassId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleNode getMerkleRoot(final NodeToSend node) {
        return node.getNode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfChildren(final NodeToSend node) {
        final MerkleNode merkleNode = node.getNode();

        if (merkleNode == null || merkleNode.isLeaf()) {
            throw new MerkleSynchronizationException("can not get number of children from node that is not internal");
        }

        return merkleNode.asInternal().getNumberOfChildren();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCustomReconnectRoot(final NodeToSend node) {
        final MerkleNode merkleNode = node.getNode();
        return merkleNode != null && merkleNode.hasCustomReconnectView();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Hash> getChildHashes(final NodeToSend parent) {

        final MerkleNode node = parent.getNode();

        if (node == null || node.isLeaf()) {
            throw new MerkleSynchronizationException("can not get child hashes of null value");
        }
        if (node.isLeaf()) {
            throw new MerkleSynchronizationException("can not get child hashes of leaf");
        }

        final MerkleInternal internal = node.asInternal();

        final int childCount = internal.getNumberOfChildren();
        final List<Hash> hashes = new ArrayList<>(childCount);

        for (int childIndex = 0; childIndex < childCount; childIndex++) {
            final MerkleNode child = internal.getChild(childIndex);

            if (child == null) {
                hashes.add(CryptographyHolder.get().getNullHash());
            } else {
                final Hash hash = child.getHash();
                if (hash == null) {
                    throw new MerkleSynchronizationException(
                            child.getClass().getName() + " at position " + child.getRoute() + " is unhashed");
                }

                hashes.add(child.getHash());
            }
        }

        return hashes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serializeLeaf(final SerializableDataOutputStream out, final NodeToSend leaf) throws IOException {
        final MerkleNode merkleNode = leaf.getNode();

        if (merkleNode == null) {
            out.writeSerializable(null, true);
            return;
        }

        if (!merkleNode.isLeaf()) {
            throw new MerkleSynchronizationException("this method can not serialize an internal node");
        }

        out.writeSerializable(merkleNode.asLeaf(), true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serializeInternal(final SerializableDataOutputStream out, final NodeToSend internal)
            throws IOException {
        final MerkleNode merkleNode = internal.getNode();

        if (merkleNode == null || merkleNode.isLeaf()) {
            throw new MerkleSynchronizationException("this method can not serialize a leaf node");
        }

        out.writeLong(merkleNode.getClassId());
    }
}
