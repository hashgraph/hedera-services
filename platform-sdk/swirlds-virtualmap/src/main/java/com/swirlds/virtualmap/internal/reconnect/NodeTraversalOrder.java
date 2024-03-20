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

package com.swirlds.virtualmap.internal.reconnect;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.synchronization.task.ReconnectNodeCount;

public interface NodeTraversalOrder {

    /**
     * Used as a return value from {@link #getNextPathToSend()} to indicate there is no path to send
     * to the teacher yet. The sending thread should wait and call {@link #getNextPathToSend()} again
     * later.
     */
    public static final long PATH_NOT_AVAILABLE_YET = -2;

    /**
     * This method is called when the first node, which is always virtual root node, is received from
     * teacher along with information about virtual tree leaves range.
     *
     * @param firstLeafPath the first leaf path in teacher's virtual tree
     * @param lastLeafPath the last leaf path in teacher's virtual tree
     * @param nodeCount object to report node stats
     */
    void start(final long firstLeafPath, final long lastLeafPath, final ReconnectNodeCount nodeCount);

    /**
     * Called by the learner's sending thread to send the next path to teacher. If this method returns
     * {@link com.swirlds.virtualmap.internal.Path#INVALID_PATH}, it indicates there are no more paths
     * to send. Other negative values like {@link #PATH_NOT_AVAILABLE_YET} indicate that there is no
     * path to send yet, but there will be some in the future, so this method needs to be called again
     * later.
     *
     * <p>This method is responsible for backpressure, if any kind of it is supported by this
     * traversal strategy. Typical implementation includes calling to {@link
     * VirtualLearnerTreeView#applySendBackpressure()}, which slows down the current thread based on
     * how many requests are sent to teacher and how many responses are received.
     *
     * @return next virtual path to send to teacher
     * @throws InterruptedException if the current thread is interrupted while backpressure waiting
     */
    long getNextPathToSend() throws InterruptedException;

    /**
     * Notifies this object that a node response is received from teacher.
     *
     * @param path received node path
     * @param isClean indicates if the node at the given path matches the corresponding node on the teacher
     */
    void nodeReceived(final long path, final boolean isClean);
}
