/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.iss;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Describes an intentional ISS that will take place at a given time on a given set of nodes.
 */
public class PlannedIss implements SelfSerializable {

    private static final long CLASS_ID = 0xb2490d3733e756dL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * The amount of time after genesis that the ISS should be triggered on.
     */
    private Duration timeAfterGenesis;

    /**
     * A list of partitions. Each partition contains a list of node IDs. All nodes in a partition will agree
     * on the state hash. Any two nodes not in the same partition will disagree on the state hash after the
     * ISS has been triggered.
     */
    private List<List<Long>> hashPartitions = new ArrayList<>();

    /**
     * Zero arg constructor for the constructable registry.
     */
    public PlannedIss() {}

    /**
     * Create a planned ISS.
     *
     * @param timeAfterGenesis
     * 		the time after genesis when the ISS should be triggered
     * @param hashPartitions
     * 		a list of ISS partitions, each partition list should be a list of node IDs that
     * 		will agree with each other on the hash of the post-ISS state
     */
    public PlannedIss(final Duration timeAfterGenesis, final List<List<Long>> hashPartitions) {
        this.timeAfterGenesis = timeAfterGenesis;
        this.hashPartitions = hashPartitions;
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
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(timeAfterGenesis.toNanos());

        out.writeInt(hashPartitions.size());
        for (final List<Long> partition : hashPartitions) {
            out.writeLongList(partition);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        timeAfterGenesis = Duration.ofNanos(in.readLong());

        hashPartitions.clear();
        final int partitionCount = in.readInt();
        for (int partitionIndex = 0; partitionIndex < partitionCount; partitionIndex++) {
            hashPartitions.add(in.readLongList(1024));
        }
    }

    /**
     * The amount of time, after genesis, when the ISS should be triggered.
     */
    public Duration getTimeAfterGenesis() {
        return timeAfterGenesis;
    }

    /**
     * Get the expected number of partitions.
     */
    public int getPartitionCount() {
        return hashPartitions.size();
    }

    /**
     * Given a node ID, find the ISS partition. If the node is not described in any partition, then it is assigned
     * the partition ID of -1.
     *
     * @param nodeId
     * 		the ID of the node
     * @return the partition of the node
     */
    public int getPartitionOfNode(final long nodeId) {
        for (int partitionIndex = 0; partitionIndex < hashPartitions.size(); partitionIndex++) {
            final List<Long> partition = hashPartitions.get(partitionIndex);
            for (final Long partitionNode : partition) {
                if (nodeId == partitionNode) {
                    return partitionIndex;
                }
            }
        }
        return -1;
    }
}
