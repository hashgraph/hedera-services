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

import com.swirlds.common.merkle.synchronization.internal.ExpectedLesson;

/**
 * Used by the learner during reconnect. These methods implement a queue like interface for expected lessons.
 *
 * @param <T>
 * 		the type of the object used to represent a merkle node within this view type
 */
public interface LearnerExpectedLessonQueue<T> {

    /**
     * <p>
     * When the teacher sends a hash (i.e. a query), it will always eventually follow up with a lesson.
     * </p>
     *
     * <p>
     * A lesson does not contain identifying information that associates it with a given node. Instead, the order
     * of the lessons matches the order of the queries, and so by using that order the correct association can be made.
     * </p>
     *
     * <p>
     * This method should add data to a queue which {@link #getNextExpectedLesson()} removes and returns from.
     * </p>
     *
     * <p>
     * If the learner already has the node and the teacher receives the response in time then the lesson will
     * just be a placeholder. This is to maintain the required ordering.
     * </p>
     *
     * <p>
     * A single thread will be reading and writing to/from this queue, so no thread safety is required.
     * </p>
     *
     * @param parent
     * 		the parent of the node in question
     * @param childIndex
     * 		the position where the node is found
     * @param original
     * 		the node originally at the specified position
     * @param nodeAlreadyPresent
     * 		true if we already have a node with the teacher's hash
     */
    void expectLessonFor(T parent, int childIndex, T original, boolean nodeAlreadyPresent);

    /**
     * Remove and return an element from the queue maintained by {@link #expectLessonFor(Object, int, Object, boolean)}.
     *
     * @return the next thing in the queue
     */
    ExpectedLesson<T> getNextExpectedLesson();

    /**
     * Check if the queue with expected lessons has a next element.
     *
     * @return true if the queue built by {@link #expectLessonFor(Object, int, Object, boolean)} is not empty
     */
    boolean hasNextExpectedLesson();
}
