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

package com.swirlds.common.merkle.synchronization.stats;

/**
 * An interface that helps gather statistics about the reconnect of tree-like data structures, such as VirtualMaps.
 * <p>
 * An implementation could gather aggregate statistics for all maps, or it could gather the counters for a specific
 * map and then also optionally delegate to another instance of the interface that would compute aggregate stats
 * for all maps.
 * <p>
 * All the methods have default no-op implementations to help with stubbing of the instances until an implementation
 * is ready, or in tests.
 */
public interface ReconnectMapStats {
    /**
     * Increment a transfers counter.
     * <p>
     * Different reconnect algorithms may define the term "transfer" differently. Examples of a transfer: <br>
     * * a lesson from the teacher, <br>
     * * a query response to the teacher, <br>
     * * a request from the learner, <br>
     * * a response from the teacher.
     *
     * @param fromTeacher a transfer of data from a teacher to the learner (e.g. a lesson, or a response)
     * @param fromLearner a transfer of data from the learner to its teacher (e.g. a query response, or a request)
     */
    default void incrementTransfers(int fromTeacher, int fromLearner) {}

    /**
     * Gather stats about internal nodes transfers.
     * @param hashNum the number of hashes of internal nodes transferred
     * @param cleanHashNum the number of hashes transferred unnecessarily because they were clean
     * @param dataNum the number of data payloads of internal nodes transferred (for non-VirtualMap trees)
     * @param cleanDataNum the number of data payloads transferred unnecessarily because they were clean
     */
    default void incrementInternalNodes(int hashNum, int cleanHashNum, int dataNum, int cleanDataNum) {}

    /**
     * Gather stats about leaf nodes transfers.
     * @param hashNum the number of hashes of leaf nodes transferred
     * @param cleanHashNum the number of hashes transferred unnecessarily because they were clean
     * @param dataNum the number of data payloads of leaf nodes transferred
     * @param cleanDataNum the number of data payloads transferred unnecessarily because they were clean
     */
    default void incrementLeafNodes(int hashNum, int cleanHashNum, int dataNum, int cleanDataNum) {}
}
