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

import com.swirlds.common.merkle.synchronization.task.ReconnectNodeCount;

public interface NodeTraversalOrder {

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
     * to send.
     *
     * @return the next virtual path to send to the teacher
     * @throws InterruptedException if the current thread is interrupted while backpressure waiting
     */
    long getNextPathToSend() throws InterruptedException;

    /**
     * Notifies this object that a node response is received from the teacher.
     *
     * @param path the received node path
     * @param isClean indicates if the node at the given path matches the corresponding node on the teacher
     */
    void nodeReceived(final long path, final boolean isClean);
}
