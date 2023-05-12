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

package com.swirlds.virtual.merkle.reconnect;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import java.io.IOException;
import java.util.List;

/**
 * An intentionally broken teacher tree view. Throws an IO exception after a certain number of nodes have been
 * serialized.
 */
public class BrokenVirtualMapTeacherView implements TeacherTreeView<Long> {

    private final TeacherTreeView<Long> baseView;
    private final int permittedInternals;
    private final int permittedLeaves;
    private int leafCount;
    private int internalCount;

    /**
     * Create a view that is intentionally broken.
     *
     * @param baseView
     * 		a functional view for a virtual map
     * @param permittedInternals
     * 		the number of internal nodes to allow to be serialized, if to many are encountered an
     * 		IO exception is thrown.
     * @param permittedLeaves
     * 		the number of leaf nodes to allow to be serialized, if to many are encountered an
     * 		IO exception is thrown
     */
    public BrokenVirtualMapTeacherView(
            final TeacherTreeView<Long> baseView, final int permittedInternals, final int permittedLeaves) {
        this.baseView = baseView;
        this.permittedInternals = permittedInternals;
        this.permittedLeaves = permittedLeaves;
    }

    @Override
    public void addToHandleQueue(final Long node) {
        baseView.addToHandleQueue(node);
    }

    @Override
    public Long getNextNodeToHandle() {
        return baseView.getNextNodeToHandle();
    }

    @Override
    public boolean areThereNodesToHandle() {
        return baseView.areThereNodesToHandle();
    }

    @Override
    public Long getChildAndPrepareForQueryResponse(final Long parent, final int childIndex) {
        return baseView.getChildAndPrepareForQueryResponse(parent, childIndex);
    }

    @Override
    public Long getNodeForNextResponse() {
        return baseView.getNodeForNextResponse();
    }

    @Override
    public boolean isResponseExpected() {
        return baseView.isResponseExpected();
    }

    @Override
    public void registerResponseForNode(final Long node, final boolean learnerHasNode) {
        baseView.registerResponseForNode(node, learnerHasNode);
    }

    @Override
    public boolean hasLearnerConfirmedFor(final Long node) {
        return baseView.hasLearnerConfirmedFor(node);
    }

    @Override
    public Long getRoot() {
        return baseView.getRoot();
    }

    @Override
    public void serializeLeaf(final SerializableDataOutputStream out, final Long leaf) throws IOException {
        leafCount++;
        if (leafCount > permittedLeaves) {
            throw new IOException("intentionally throwing during leaf serialization");
        }

        baseView.serializeLeaf(out, leaf);
    }

    @Override
    public void serializeInternal(final SerializableDataOutputStream out, final Long internal) throws IOException {
        internalCount++;
        if (internalCount > permittedInternals) {
            throw new IOException("intentionally throwing during internal serialization");
        }

        baseView.serializeInternal(out, internal);
    }

    @Override
    public List<Hash> getChildHashes(final Long parent) {
        return baseView.getChildHashes(parent);
    }

    @Override
    public boolean isCustomReconnectRoot(final Long node) {
        return baseView.isCustomReconnectRoot(node);
    }

    @Override
    public boolean isInternal(final Long node, final boolean isOriginal) {
        return baseView.isInternal(node, isOriginal);
    }

    @Override
    public int getNumberOfChildren(final Long node) {
        return baseView.getNumberOfChildren(node);
    }

    @Override
    public long getClassId(final Long node) {
        return baseView.getClassId(node);
    }

    @Override
    public MerkleNode getMerkleRoot(final Long node) {
        return baseView.getMerkleRoot(node);
    }
}
