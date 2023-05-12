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

package com.swirlds.common.test.merkle.dummy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.internal.NodeToSend;
import com.swirlds.common.merkle.synchronization.views.CustomReconnectRoot;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import java.util.LinkedList;
import java.util.List;

/**
 * A test class for probing the behavior of self reconnecting nodes.
 */
public class DummyCustomReconnectRoot extends DummyMerkleInternal
        implements CustomReconnectRoot<NodeToSend, MerkleNode> {

    private static final long CLASS_ID = 0x3d03994ec6d42dccL;

    private final List<DummyTeacherTreeView> views;

    public DummyCustomReconnectRoot() {
        views = new LinkedList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasCustomReconnectView() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TeacherTreeView<NodeToSend> buildTeacherView() {
        final DummyTeacherTreeView view = new DummyTeacherTreeView(this);
        views.add(view);
        return view;
    }

    /**
     * Throw an exception if all views have not yet been closed.
     */
    public void assertViewsAreClosed() {
        for (final DummyTeacherTreeView view : views) {
            assertTrue(view.isClosed(), "view should have been closed");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setupWithOriginalNode(final MerkleNode originalNode) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void setupWithNoData() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public LearnerTreeView<MerkleNode> buildLearnerView() {
        return new DummyLearnerTreeView(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DummyCustomReconnectRoot copy() {
        throwIfImmutable();
        setImmutable(true);
        return new DummyCustomReconnectRoot();
    }

    @Override
    public String getValue() {
        return null;
    }
}
