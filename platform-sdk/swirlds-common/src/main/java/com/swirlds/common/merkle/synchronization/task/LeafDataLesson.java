/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.synchronization.task;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import java.io.IOException;

/**
 * This lesson describes a leaf node.
 *
 * @param <T>
 * 		the type used by the view to represent a node
 */
public class LeafDataLesson<T> implements SelfSerializable {

    private static final long CLASS_ID = 0xafbdd5560579cb02L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private TeacherTreeView<T> teacherView;
    private LearnerTreeView<T> learnerTreeView;
    private T leaf;

    /**
     * Zero arg constructor for constructable registry.
     */
    public LeafDataLesson() {}

    /**
     * This constructor is used by the learner when deserializing.
     *
     * @param learnerTreeView
     * 		the view for the learner
     */
    public LeafDataLesson(final LearnerTreeView<T> learnerTreeView) {
        this.learnerTreeView = learnerTreeView;
    }

    /**
     * Create a new lesson for a leaf node.
     *
     * @param teacherView
     * 		the view for the teacher
     * @param leaf
     * 		the leaf to send in the lesson
     */
    public LeafDataLesson(final TeacherTreeView<T> teacherView, final T leaf) {
        this.teacherView = teacherView;
        this.leaf = leaf;
    }

    /**
     * Get the leaf contained by this lesson.
     */
    public T getLeaf() {
        return leaf;
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
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        teacherView.serializeLeaf(out, leaf);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        leaf = learnerTreeView.deserializeLeaf(in);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getClassVersion() {
        return ClassVersion.ORIGINAL;
    }
}
