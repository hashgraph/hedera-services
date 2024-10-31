/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Describes an intentional ISS that will take place at a given time on a given set of nodes.
 */
public class PlannedIss implements SelfSerializable, PlannedIncident {
    private static final Logger logger = LogManager.getLogger(PlannedIss.class);

    private static final long CLASS_ID = 0xb2490d3733e756dL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * The amount of time after genesis that the ISS should be triggered on.
     */
    private Duration timeAfterGenesis;

    /**
     * A list of partitions. Each partition contains a list of node IDs. All nodes in a partition will agree on the
     * state hash. Any two nodes not in the same partition will disagree on the state hash after the ISS has been
     * triggered.
     */
    private List<List<NodeId>> hashPartitions = new ArrayList<>();

    /**
     * Zero arg constructor for the constructable registry.
     */
    public PlannedIss() {}

    /**
     * Create a planned ISS.
     *
     * @param timeAfterGenesis the time after genesis when the ISS should be triggered
     * @param hashPartitions   a list of ISS partitions, each partition list should be a list of node IDs that will
     *                         agree with each other on the hash of the post-ISS state
     */
    public PlannedIss(final Duration timeAfterGenesis, final List<List<NodeId>> hashPartitions) {
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
    public int getClassVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(timeAfterGenesis.toNanos());

        out.writeInt(hashPartitions.size());
        for (final List<NodeId> partition : hashPartitions) {
            out.writeSerializableList(partition, false, true);
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
            hashPartitions.add(in.readSerializableList(1024, false, NodeId::new));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Duration getTimeAfterGenesis() {
        return timeAfterGenesis;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getDescriptor() {
        return "ISS";
    }

    /**
     * Get the expected number of partitions.
     */
    public int getPartitionCount() {
        return hashPartitions.size();
    }

    /**
     * Given a node ID, find the ISS partition. If the node is not described in any partition, then it is assigned the
     * partition ID of -1.
     *
     * @param nodeId the ID of the node
     * @return the partition of the node
     */
    public int getPartitionOfNode(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId);

        for (int partitionIndex = 0; partitionIndex < hashPartitions.size(); partitionIndex++) {
            final List<NodeId> partition = hashPartitions.get(partitionIndex);
            for (final NodeId partitionNode : partition) {
                if (nodeId.equals(partitionNode)) {
                    return partitionIndex;
                }
            }
        }
        return -1;
    }

    /**
     * Create a new {@link PlannedIss} from a string representation
     * <p>
     * String example:
     * </p>
     * <pre>
     * 1234:0-1+2+3
     * </pre>
     * <p>
     * The first number is a time, in seconds, after genesis, when the ISS will be triggered. Here
     * the ISS will be triggered 1234 seconds after genesis (measured by consensus time).
     * </p>
     * <p>
     * The time MUST be followed by a ":".
     * </p>
     * <p>
     * Next is a "-" separated list of ISS partitions. Each ISS partition will agree with other nodes in the same
     * partition, and disagree with any node not in the partition. In this example, node 0 is in an ISS partition by
     * itself, and nodes 1 2 and 3 are in a partition together. Nodes in the same partition should be separated by
     * a "+" symbol.
     * </p>
     * <p>
     * A few more examples:
     * </p>
     * <ul>
     * <li>
     * "60:0-1-2-3": 60 seconds after the app is started, all nodes disagree with all other nodes
     * </li>
     * <li>
     * "600:0+1-2+3": 10 minutes after start, the network splits in half. 0 and 1 agree, 2 and 3 agree.
     * </li>
     * <li>
     * "120:0+1-2+3-4+5+6": a seven node network. The ISS is triggered 120 seconds after start. Nodes 0 and 1
     * agree with each other, nodes 2 and 3 agree with each other, and nodes 4 5 and 6 agree with each other.
     * </li>
     * </ul>
     *
     * @param plannedIssString the string representation
     * @return the new {@link PlannedIss}
     */
    @NonNull
    public static PlannedIss fromString(@NonNull final String plannedIssString) {
        final String[] timestampAndPartitionsStrings = plannedIssString.strip().split(":");

        final int elapsedTime = Integer.parseInt(timestampAndPartitionsStrings[0]);
        final String partitionsString = timestampAndPartitionsStrings[1];
        final String[] partitionStrings = partitionsString.split("-");

        final Set<NodeId> uniqueNodeIds = new HashSet<>();
        final List<List<NodeId>> partitions = new ArrayList<>(partitionStrings.length);
        for (final String partitionString : partitionStrings) {
            final String[] nodeStrings = partitionString.split("\\+");

            final List<NodeId> nodes = new ArrayList<>();
            for (final String nodeString : nodeStrings) {
                final NodeId nodeId = NodeId.of(Long.parseLong(nodeString));
                nodes.add(nodeId);
                if (!uniqueNodeIds.add(nodeId)) {
                    logger.error(EXCEPTION.getMarker(), "Node {} appears more than once in ISS description!", nodeId);
                }
            }
            partitions.add(nodes);
        }

        return new PlannedIss(Duration.ofSeconds(elapsedTime), partitions);
    }
}
