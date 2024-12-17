/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.fixtures.merkle.dummy;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.stats.ReconnectMapStats;
import com.swirlds.common.merkle.synchronization.views.LearnerPushMerkleTreeView;
import edu.umd.cs.findbugs.annotations.NonNull;

public class DummyLearnerPushMerkleTreeView extends LearnerPushMerkleTreeView {

    /**
     * Create a new standard tree view out of an in-memory merkle tree (or subtree).
     *
     * @param root
     * 		the root of the tree (or subtree)
     * @param mapStats
     *      a ReconnectMapStats object to collect reconnect metrics
     */
    public DummyLearnerPushMerkleTreeView(
            final ReconnectConfig reconnectConfig, final MerkleNode root, @NonNull final ReconnectMapStats mapStats) {
        super(reconnectConfig, root, mapStats);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRootOfState() {
        return false;
    }
}
