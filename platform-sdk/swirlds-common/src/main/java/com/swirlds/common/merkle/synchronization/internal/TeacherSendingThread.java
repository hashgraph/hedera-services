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

package com.swirlds.common.merkle.synchronization.internal;

import static com.swirlds.common.merkle.synchronization.internal.LessonType.CUSTOM_VIEW_ROOT;
import static com.swirlds.common.merkle.synchronization.internal.LessonType.INTERNAL_NODE_DATA;
import static com.swirlds.common.merkle.synchronization.internal.LessonType.LEAF_NODE_DATA;
import static com.swirlds.common.merkle.synchronization.internal.LessonType.NODE_IS_UP_TO_DATE;
import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.CustomReconnectRoot;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class encapsulates all logic for the teacher's sending thread.
 *
 * @param <T>
 * 		the type of data used by the view to represent a node
 */
public class TeacherSendingThread<T> {

    private static final Logger logger = LogManager.getLogger(TeacherSendingThread.class);

    private static final String NAME = "sender";

    /**
     * The lesson used to describe an up to date node is always eactly the same. No need to create a new
     * object each time.
     */
    private static final Lesson<?> UP_TO_DATE_LESSON = new Lesson<>(NODE_IS_UP_TO_DATE, null);

    private final StandardWorkGroup workGroup;
    private final AsyncInputStream<QueryResponse> in;
    private final AsyncOutputStream<Lesson<T>> out;
    private final Queue<TeacherSubtree> subtrees;
    private final TeacherTreeView<T> view;
    private final AtomicBoolean senderIsFinished;

    /**
     * Create new thread that will send data lessons and queries for a subtree.
     *
     * @param workGroup
     * 		the work group managing the reconnect
     * @param in
     * 		the input stream
     * @param out
     * 		the output stream, this object is responsible for closing this object when finished
     * @param subtrees
     * 		a queue containing roots of subtrees to send, may have more roots added by this class
     * @param view
     * 		an object that interfaces with the subtree
     * @param senderIsFinished
     * 		set to true when this thread has finished
     */
    public TeacherSendingThread(
            final StandardWorkGroup workGroup,
            final AsyncInputStream<QueryResponse> in,
            final AsyncOutputStream<Lesson<T>> out,
            final Queue<TeacherSubtree> subtrees,
            final TeacherTreeView<T> view,
            final AtomicBoolean senderIsFinished) {

        this.workGroup = workGroup;
        this.in = in;
        this.out = out;
        this.subtrees = subtrees;
        this.view = view;
        this.senderIsFinished = senderIsFinished;
    }

    /**
     * Start the thread that sends lessons and queries to the learner.
     */
    public void start() {
        workGroup.execute(NAME, this::run);
    }

    /**
     * When a {@link Lesson} for in an internal node is sent, that lesson contains embedded queries. This method
     * prepares for the responses to those queries.
     */
    private void prepareForQueryResponse(final T parent, final int childIndex) {
        in.anticipateMessage();
        final T child = view.getChildAndPrepareForQueryResponse(parent, childIndex);
        view.addToHandleQueue(child);
    }

    /**
     * Send a lesson that describes the root of a subtree with a custom view.
     */
    private Lesson<T> buildCustomReconnectRootLesson(final T node) {
        final Lesson<T> lesson = new Lesson<>(CUSTOM_VIEW_ROOT, new CustomViewRootLesson(view.getClassId(node)));
        final CustomReconnectRoot<?, ?> subtreeRoot = (CustomReconnectRoot<?, ?>) view.getMerkleRoot(node);

        subtrees.add(new TeacherSubtree(subtreeRoot, subtreeRoot.buildTeacherView()));

        return lesson;
    }

    /**
     * Send a lesson that contains data for a leaf or an internal node.
     */
    private Lesson<T> buildDataLesson(final T node) {
        final Lesson<T> lesson;
        if (view.isInternal(node, true)) {
            lesson = new Lesson<>(INTERNAL_NODE_DATA, new InternalDataLesson<>(view, node));
            final int childCount = view.getNumberOfChildren(node);
            for (int childIndex = 0; childIndex < childCount; childIndex++) {
                prepareForQueryResponse(node, childIndex);
            }
        } else {
            lesson = new Lesson<>(LEAF_NODE_DATA, new LeafDataLesson<>(view, node));
        }

        return lesson;
    }

    /**
     * <p>
     * Send a lesson about a node. Each query sent to the learner is always followed by a lesson (eventually).
     * Some lessons are just confirmations that the learner has the data. Others actually contain the data required
     * by the learner to reconstruct the node.
     * </p>
     *
     * <p>
     * Lessons containing data about an internal node may also contain queries. The queries will be for the children
     * of the internal node.
     * </p>
     */
    @SuppressWarnings("unchecked")
    private void sendLesson(final T node) throws InterruptedException {
        final Lesson<T> lesson;

        final boolean learnerHasConfirmed = view.hasLearnerConfirmedFor(node);

        if (learnerHasConfirmed) {
            lesson = (Lesson<T>) UP_TO_DATE_LESSON;
        } else if (view.isCustomReconnectRoot(node)) {
            lesson = buildCustomReconnectRootLesson(node);
        } else {
            lesson = buildDataLesson(node);
        }

        out.sendAsync(lesson);
    }

    /**
     * This thread is responsible for sending lessons (and nested queries) to the learner.
     */
    private void run() {
        try (out) {
            out.sendAsync(buildDataLesson(view.getRoot()));

            while (view.areThereNodesToHandle()) {
                final T node = view.getNextNodeToHandle();
                sendLesson(node);
            }
        } catch (final InterruptedException ex) {
            logger.warn(RECONNECT.getMarker(), "teacher's sending thread interrupted");
            Thread.currentThread().interrupt();
        } catch (final Exception ex) {
            throw new MerkleSynchronizationException("exception in the teacher's receiving thread", ex);
        } finally {
            senderIsFinished.set(true);
        }
    }
}
