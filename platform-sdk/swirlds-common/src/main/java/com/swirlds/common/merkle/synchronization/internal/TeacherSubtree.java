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

package com.swirlds.common.merkle.synchronization.internal;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.views.StandardTeacherTreeView;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;

/**
 * A subtree that needs to be sent by the teacher.
 */
public final class TeacherSubtree implements AutoCloseable {

    private final MerkleNode root;
    private final TeacherTreeView<?> view;

    /**
     * Create a subtree with {@link StandardTeacherTreeView}.
     *
     * @param root
     * 		the root of the subtree
     */
    public TeacherSubtree(final MerkleNode root) {
        this(root, new StandardTeacherTreeView(root));
    }

    /**
     * Create an object that tracks a subtree that needs to be sent by the teacher.
     *
     * @param root
     * 		the root of the subtree
     * @param view
     * 		the view to be used by the subtree
     */
    public TeacherSubtree(final MerkleNode root, final TeacherTreeView<?> view) {
        this.root = root;
        this.view = view;

        if (root != null) {
            root.reserve();
        }
    }

    /**
     * Get the root of the subtree.
     *
     * @return the root
     */
    public MerkleNode getRoot() {
        return root;
    }

    /**
     * Get the view to be used for the subtree.
     *
     * @return a teacher view
     */
    public TeacherTreeView<?> getView() {
        return view;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        view.close();
        if (root != null) {
            root.release();
        }
    }
}
