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

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.views.StandardTeacherTreeView;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * View for testing.
 */
public class DummyTeacherTreeView extends StandardTeacherTreeView {

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Create a view for a standard merkle tree.
     *
     * @param root
     * 		the root of the tree
     */
    public DummyTeacherTreeView(final MerkleNode root) {
        super(root);
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
