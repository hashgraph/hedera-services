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

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.views.TeacherPushMerkleTreeView;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * View for testing.
 */
public class DummyTeacherPushMerkleTreeView extends TeacherPushMerkleTreeView {

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Create a view for a standard merkle tree.
     *
     * @param configuration the configuration
     * @param root          the root of the tree
     */
    public DummyTeacherPushMerkleTreeView(@NonNull final Configuration configuration, final MerkleNode root) {
        super(configuration, root);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        assertFalse(closed.get(), "should only be closed once");
        closed.set(true);
    }

    /**
     * Check if this view has been closed.
     */
    public boolean isClosed() {
        return closed.get();
    }
}
