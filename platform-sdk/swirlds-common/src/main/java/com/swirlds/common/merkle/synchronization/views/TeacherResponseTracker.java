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
 * This interface contains methods that are used by the teacher during reconnect to track the responses provided by
 * the learner, and to build a working theory of the learner's knowledge.
 *
 * @param <T>
 * 		the type of the object used by the view to represent a merkle node
 */
public interface TeacherResponseTracker<T> {

    /**
     * Register the result of the learner's response to a query
     *
     * @param node
     * 		the node in question
     * @param learnerHasNode
     * 		true if the learner has the node, otherwise false
     */
    void registerResponseForNode(T node, boolean learnerHasNode);

    /**
     * Check if the learner has confirmed that it has a given node. Should return false if the learner has responded
     * with "no" or if the learner has not yet responded.
     *
     * @param node
     * 		the node in question
     * @return true if a message has been received showing that the learner already has the node
     */
    boolean hasLearnerConfirmedFor(T node);
}
