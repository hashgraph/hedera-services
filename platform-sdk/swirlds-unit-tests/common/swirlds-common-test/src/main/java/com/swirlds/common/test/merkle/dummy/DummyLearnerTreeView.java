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

package com.swirlds.common.test.merkle.dummy;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.views.StandardLearnerTreeView;

public class DummyLearnerTreeView extends StandardLearnerTreeView {

    /**
     * Create a new standard tree view out of an in-memory merkle tree (or subtree).
     *
     * @param root
     * 		the root of the tree (or subtree)
     */
    public DummyLearnerTreeView(final MerkleNode root) {
        super(root);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRootOfState() {
        return false;
    }
}
