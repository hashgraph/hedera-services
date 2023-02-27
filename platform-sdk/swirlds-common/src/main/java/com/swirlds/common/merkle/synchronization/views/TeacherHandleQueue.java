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

package com.swirlds.common.merkle.synchronization.views;

/**
 * Describes methods that implement a queue of nodes to be handled by the teacher during reconnect. Nodes are placed
 * into this queue when they are sent in a query, and handled when it is time to send the lesson for the node.
 *
 * @param <T>
 * 		a type that represents a merkle node in the view
 */
public interface TeacherHandleQueue<T> {

    /**
     * <p>
     * Add the node to a queue so that it can be later handled. Nodes added to this queue should be
     * returned by {@link #getNextNodeToHandle()}. Queue does not need to be thread safe.
     * </p>
     *
     * <p>
     * May not be called on all nodes. For the nodes it is called on, the order will be consistent with
     * a breadth first ordering.
     * </p>
     *
     * @param node
     * 		the node to add to the queue
     */
    void addToHandleQueue(T node);

    /**
     * Remove and return the next node from the queue built by {@link #addToHandleQueue(Object)}.
     *
     * @return the next node to handle
     */
    T getNextNodeToHandle();

    /**
     * Check if there is anything in the queue built by {@link #addToHandleQueue(Object)}.
     *
     * @return true if there are nodes in the queue
     */
    boolean areThereNodesToHandle();
}
