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

import com.swirlds.common.merkle.MerkleInternal;

/**
 * Methods used by the learner to initialize nodes within a view.
 *
 * @param <T>
 * 		the type of object used to represent merkle nodes in the view
 */
public interface LearnerInitializer<T> {

    /**
     * Mark a node for later initialization. If a node type is known not to require
     * initialization, no action is required.
     *
     * @param node
     * 		the node to later initialize
     */
    void markForInitialization(T node);

    /**
     * <p>
     * Initialize each internal node that was reconstructed via this algorithm by calling
     * {@link MerkleInternal#rebuild()}. No action is required for node types that are known
     * to not require initialization.
     * </p>
     *
     * <p>
     * Initialization is required to initialize children before their parents.
     * </p>
     *
     * <p>
     * This method is called exactly once when the synchronization algorithm has completely transferred the entire tree.
     * </p>
     *
     * <p>
     * It is ok if this method is also used to initialize other parts or components of the tree.
     * </p>
     */
    void initialize();
}
