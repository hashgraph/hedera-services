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

package com.swirlds.common.merkle.synchronization.views;

import com.swirlds.common.merkle.MerkleNode;

/**
 * Nodes that are want to use a custom view for reconnect must extend this interface and
 * return true for {@link MerkleNode#hasCustomReconnectView() hasCustomReconnectView()}.
 *
 * @param <T>
 * 		the type used by the view for the teacher
 * @param <L>
 * 		the type used by the view for the learner
 */
public interface CustomReconnectRoot<T, L> extends MerkleNode {

    /**
     * <p>
     * Build a view of this subtree to be used for reconnect by the teacher.
     * </p>
     *
     * <p>
     * It is ok if this view is not immediately ready for use, as long as the view eventually
     * becomes ready for use (presumably when some background task has completed). If this is
     * the case, then the {@link TeacherTreeView#waitUntilReady()} on the returned view should
     * block until the view is ready to be used to perform a reconnect.
     * </p>
     *
     * @return a view representing this subtree
     */
    TeacherTreeView<T> buildTeacherView();

    /**
     * Build a view of this subtree to be used for reconnect by the learner.
     *
     * @return a view representing this subtree
     */
    LearnerTreeView<L> buildLearnerView();

    /**
     * If the original node in this position is of the correct type then the learner's node is initialized via
     * this method. After this method is called, this node and its subtree should be entirely hashed, mutable,
     * and should not cause the original to modified.
     *
     * @param originalNode
     * 		the original node in the learner's tree in the root position of this subtree
     */
    void setupWithOriginalNode(final MerkleNode originalNode);

    /**
     * Called on a node if there is no data to be copied.
     */
    void setupWithNoData();

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean hasCustomReconnectView() {
        return true;
    }
}
