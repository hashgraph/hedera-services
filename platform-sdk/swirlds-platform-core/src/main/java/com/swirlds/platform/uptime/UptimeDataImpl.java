/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.uptime;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.events.ConsensusEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Contains the uptime data for the network.
 */
public class UptimeDataImpl implements FastCopyable, SelfSerializable, MutableUptimeData {

    private static final Logger logger = LogManager.getLogger(UptimeDataImpl.class);

    private static final long CLASS_ID = 0x1f13fa8c89b27a8cL;

    private static final int MAX_NODE_COUNT = 1024;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int SELF_SERIALIZABLE_NODE_ID = 2;
    }

    private final SortedMap<NodeId, NodeUptimeData> data = new TreeMap<>();

    /**
     * Zero arg constructor required by serialization engine.
     */
    public UptimeDataImpl() {}

    /**
     * Copy constructor.
     */
    private UptimeDataImpl(@NonNull final UptimeDataImpl other) {
        for (final Entry<NodeId, NodeUptimeData> entry : other.data.entrySet()) {
            data.put(entry.getKey(), entry.getValue().copy());
        }
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
        return ClassVersion.SELF_SERIALIZABLE_NODE_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Instant getLastEventTime(@NonNull final NodeId id) {
        Objects.requireNonNull(id, "id must not be null");
        final NodeUptimeData nodeData = data.get(id);
        if (nodeData == null) {
            return null;
        }
        return nodeData.getLastEventTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastEventRound(@NonNull final NodeId id) {
        Objects.requireNonNull(id, "id must not be null");
        final NodeUptimeData nodeData = data.get(id);
        if (nodeData == null) {
            return NO_ROUND;
        }
        return nodeData.getLastEventRound();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Instant getLastJudgeTime(@NonNull final NodeId id) {
        Objects.requireNonNull(id, "id must not be null");
        final NodeUptimeData nodeData = data.get(id);
        if (nodeData == null) {
            return null;
        }
        return nodeData.getLastJudgeTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastJudgeRound(@NonNull final NodeId id) {
        Objects.requireNonNull(id, "id must not be null");
        final NodeUptimeData nodeData = data.get(id);
        if (nodeData == null) {
            return NO_ROUND;
        }
        return nodeData.getLastJudgeRound();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Set<NodeId> getTrackedNodes() {
        return new HashSet<>(data.keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordLastEvent(@NonNull final ConsensusEvent event, final long round) {
        final NodeUptimeData nodeData = data.get(event.getCreatorId());
        if (nodeData == null) {
            logger.warn(
                    EXCEPTION.getMarker(), "Node {} is not being tracked by the uptime tracker.", event.getCreatorId());
            return;
        }

        nodeData.setLastEventRound(round).setLastEventTime(event.getConsensusTimestamp());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordLastJudge(@NonNull final ConsensusEvent event, final long round) {
        final NodeUptimeData nodeData = data.get(event.getCreatorId());
        if (nodeData == null) {
            logger.warn(
                    EXCEPTION.getMarker(), "Node {} is not being tracked by the uptime tracker.", event.getCreatorId());
            return;
        }
        nodeData.setLastJudgeRound(round).setLastJudgeTime(event.getConsensusTimestamp());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addNode(@NonNull final NodeId node) {
        Objects.requireNonNull(node, "node must not be null");
        data.put(node, new NodeUptimeData());
    }

    @Override
    public void removeNode(@NonNull final NodeId node) {
        Objects.requireNonNull(node, "node must not be null");
        data.remove(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeInt(data.size());
        for (final Entry<NodeId, NodeUptimeData> entry : data.entrySet()) {
            out.writeSerializable(entry.getKey(), false);
            out.writeSerializable(entry.getValue(), false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        final int lastConsensusEventTimesSize = in.readInt();
        if (lastConsensusEventTimesSize > MAX_NODE_COUNT) {
            // Safety sanity check, don't let an attacker force us to allocate too much memory.
            throw new IOException("too many nodes");
        }

        for (int i = 0; i < lastConsensusEventTimesSize; i++) {
            final NodeId nodeId;
            if (version < ClassVersion.SELF_SERIALIZABLE_NODE_ID) {
                nodeId = new NodeId(in.readLong());
            } else {
                nodeId = in.readSerializable(false, NodeId::new);
            }
            data.put(nodeId, in.readSerializable(false, NodeUptimeData::new));
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public UptimeDataImpl copy() {
        return new UptimeDataImpl(this);
    }
}
