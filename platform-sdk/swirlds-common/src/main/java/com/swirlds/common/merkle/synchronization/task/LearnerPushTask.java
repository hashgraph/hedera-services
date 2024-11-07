/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.constructable.ClassIdFormatter.classIdString;
import static com.swirlds.logging.legacy.LogMarker.RECONNECT;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.stats.ReconnectMapStats;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.CustomReconnectRoot;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.common.utility.ThresholdLimitingHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class manages the learner's work task for synchronization.
 *
 * @param <T>
 * 		the type of data used by the view to represent a node
 */
public class LearnerPushTask<T> {

    private static final Logger logger = LogManager.getLogger(LearnerPushTask.class);

    private static final String NAME = "learner-task";

    private final StandardWorkGroup workGroup;
    private final int viewId;
    private final AsyncInputStream in;
    private final AsyncOutputStream out;
    private final AtomicReference<MerkleNode> root;
    private final LearnerTreeView<T> view;
    private final ReconnectNodeCount nodeCount;

    private final Consumer<CustomReconnectRoot<?, ?>> subtreeListener;

    private final ReconnectMapStats mapStats;

    private final ThresholdLimitingHandler<Throwable> exceptionRateLimiter = new ThresholdLimitingHandler<>(1);

    private final Consumer<Integer> completeListener;

    /**
     * Create a new thread for the learner.
     *
     * @param workGroup
     * 		the work group that will manage the thread
     * @param in
     * 		the input stream, this object is responsible for closing the stream when finished
     * @param out
     * 		the output stream, this object is responsible for closing the stream when finished
     * @param subtreeListener
     * 		a listener to notify if custom tree views are encountered
     * @param root
     * 		a reference which will eventually hold the root of this subtree
     * @param view
     * 		a view used to interface with the subtree
     * @param nodeCount
     * 		an object used to keep track of the number of nodes sent during the reconnect
     * @param mapStats
     *      a ReconnectMapStats object to collect reconnect metrics
     */
    public LearnerPushTask(
            final StandardWorkGroup workGroup,
            final int viewId,
            final AsyncInputStream in,
            final AsyncOutputStream out,
            final Consumer<CustomReconnectRoot<?, ?>> subtreeListener,
            final AtomicReference<MerkleNode> root,
            final LearnerTreeView<T> view,
            final ReconnectNodeCount nodeCount,
            final Consumer<Integer> completeListener,
            @NonNull final ReconnectMapStats mapStats) {
        this.workGroup = workGroup;
        this.viewId = viewId;
        this.in = in;
        this.out = out;
        this.subtreeListener = subtreeListener;
        this.root = root;
        this.view = view;
        this.nodeCount = nodeCount;
        this.completeListener = completeListener;
        this.mapStats = mapStats;
    }

    public void start() {
        workGroup.execute(NAME, this::run);
    }

    /**
     * Handle a lesson for the root of a tree with a custom view. When such a node is encountered, instead of iterating
     * over its children, the node is put into a queue for later handling. Eventually that node and the subtree
     * below it are synchronized using the specified view.
     */
    private T handleCustomRootInitialLesson(
            final LearnerTreeView<T> view, final ExpectedLesson<T> expectedLesson, final Lesson<T> lesson) {

        // The original node is the node at the exact same position in the learner's original tree.
        // If the hash matches the original can be used in the new tree.
        // Sometimes the original node is null if the original tree does not have any node in this position.
        final T originalNode = expectedLesson.getOriginalNode();

        final CustomReconnectRoot<?, ?> customRoot =
                ConstructableRegistry.getInstance().createObject(lesson.getCustomViewClassId());
        if (customRoot == null) {
            throw new MerkleSynchronizationException(
                    "unable to construct object with class ID " + classIdString(lesson.getCustomViewClassId()));
        }

        if (originalNode != null && view.getClassId(originalNode) == lesson.getCustomViewClassId()) {
            customRoot.setupWithOriginalNode(view.getMerkleRoot(originalNode));
        } else {
            customRoot.setupWithNoData();
        }

        subtreeListener.accept(customRoot);
        return view.convertMerkleRootToViewType(customRoot);
    }

    /**
     * Based on the data in a lesson, get the node that should be inserted into the tree.
     */
    private T extractNodeFromLesson(
            final LearnerTreeView<T> view,
            final ExpectedLesson<T> expectedLesson,
            final Lesson<T> lesson,
            boolean firstLesson) {

        if (lesson.isCurrentNodeUpToDate()) {
            // We already have the correct node in our tree.
            return expectedLesson.getOriginalNode();
        } else if (lesson.isCustomViewRoot()) {
            // This node is the root of a subtree with a custom view,
            // but we are not yet iterating over that subtree.
            return handleCustomRootInitialLesson(view, expectedLesson, lesson);
        } else {
            final T node;

            if (firstLesson && !view.isRootOfState()) {
                // Special case: roots of subtrees with custom views will have been copied
                // when synchronizing the parent tree.
                node = expectedLesson.getOriginalNode();
            } else {
                // The teacher sent us the node we should use
                node = lesson.getNode();
            }

            if (lesson.isInternalLesson()) {
                view.markForInitialization(node);
            }

            return node;
        }
    }

    /**
     * Handle queries associated with a lesson.
     */
    private void handleQueries(
            final LearnerTreeView<T> view,
            final AsyncInputStream in,
            final AsyncOutputStream out,
            final List<Hash> queries,
            final T originalParent,
            final T newParent)
            throws InterruptedException {

        final int childCount = queries.size();
        for (int childIndex = 0; childIndex < childCount; childIndex++) {

            final T originalChild;
            if (view.isInternal(originalParent, true) && view.getNumberOfChildren(originalParent) > childIndex) {
                originalChild = view.getChild(originalParent, childIndex);
            } else {
                originalChild = null;
            }

            final Hash originalHash = view.getNodeHash(originalChild);

            final Hash teacherHash = queries.get(childIndex);
            if (originalHash == null) {
                exceptionRateLimiter.handle(
                        new NullPointerException(),
                        (error) ->
                                logger.warn(RECONNECT.getMarker(), "originalHash for node {} is null", originalChild));
            }
            final boolean nodeAlreadyPresent = originalHash != null && originalHash.equals(teacherHash);
            out.sendAsync(viewId, new QueryResponse(nodeAlreadyPresent));
            mapStats.incrementTransfersFromLearner();
            view.recordHashStats(mapStats, newParent, childIndex, nodeAlreadyPresent);

            view.expectLessonFor(newParent, childIndex, originalChild, nodeAlreadyPresent);
        }
    }

    /**
     * Update node counts for statistics.
     */
    private void addToNodeCount(final ExpectedLesson<T> expectedLesson, final Lesson<T> lesson, final T newChild) {
        if (lesson.isLeafLesson()) {
            mapStats.incrementLeafData(1, expectedLesson.isNodeAlreadyPresent() ? 1 : 0);
        }

        if (lesson.isCurrentNodeUpToDate()) {
            return;
        }

        if (view.isInternal(newChild, false)) {
            nodeCount.incrementInternalCount();
            if (expectedLesson.isNodeAlreadyPresent()) {
                nodeCount.incrementRedundantInternalCount();
            }
        } else {
            nodeCount.incrementLeafCount();
            if (expectedLesson.isNodeAlreadyPresent()) {
                nodeCount.incrementRedundantLeafCount();
            }
        }
    }

    /**
     * Get the tree/subtree from the teacher.
     */
    private void run() {
        boolean firstLesson = true;

        final Supplier<Lesson<T>> messageFactory = () -> new Lesson<>(view);
        try (view) {
            view.expectLessonFor(null, 0, view.getOriginalRoot(), false);

            while (view.hasNextExpectedLesson() && !Thread.currentThread().isInterrupted()) {

                final ExpectedLesson<T> expectedLesson = view.getNextExpectedLesson();
                final Lesson<T> lesson = in.readAnticipatedMessage(viewId, messageFactory);
                mapStats.incrementTransfersFromTeacher();

                final T parent = expectedLesson.getParent();

                final T newChild = extractNodeFromLesson(view, expectedLesson, lesson, firstLesson);

                firstLesson = false;

                if (parent == null) {
                    root.set(view.getMerkleRoot(newChild));
                } else {
                    view.setChild(parent, expectedLesson.getPositionInParent(), newChild);
                }

                addToNodeCount(expectedLesson, lesson, newChild);

                if (lesson.hasQueries()) {
                    final List<Hash> queries = lesson.getQueries();
                    handleQueries(view, in, out, queries, expectedLesson.getOriginalNode(), newChild);
                }
            }

            logger.info(RECONNECT.getMarker(), "learner thread finished the learning loop for the current subtree");
        } catch (final InterruptedException ex) {
            logger.warn(RECONNECT.getMarker(), "Learner thread interrupted");
            Thread.currentThread().interrupt();
        } catch (final Exception ex) {
            workGroup.handleError(ex);
        } finally {
            completeListener.accept(viewId);
        }

        logger.info(RECONNECT.getMarker(), "learner thread closed view for the current subtree");
    }
}
