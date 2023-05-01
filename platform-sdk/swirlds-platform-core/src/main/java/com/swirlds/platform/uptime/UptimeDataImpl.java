/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map.Entry;
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
    }

    private final SortedMap<Long, NodeUptimeData> data = new TreeMap<>();

    /**
     * Zero arg constructor required by serialization engine.
     */
    public UptimeDataImpl() {}

    /**
     * Copy constructor.
     */
    private UptimeDataImpl(@NonNull final UptimeDataImpl other) {
        for (final Entry<Long, NodeUptimeData> entry : other.data.entrySet()) {
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
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Instant getLastEventTime(final long id) {
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
    public long getLastEventRound(final long id) {
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
    public Instant getLastJudgeTime(final long id) {
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
    public long getLastJudgeRound(final long id) {
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
    public Set<Long> getTrackedNodes() {
        return new HashSet<>(data.keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordLastEvent(@NonNull final EventImpl event) {
        final NodeUptimeData nodeData = data.get(event.getCreatorId());
        if (nodeData == null) {
            logger.warn(
                    EXCEPTION.getMarker(), "Node {} is not being tracked by the uptime tracker.", event.getCreatorId());
            return;
        }

        nodeData.setLastEventRound(event.getRoundReceived()).setLastEventTime(event.getConsensusTimestamp());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordLastJudge(@NonNull final EventImpl event) {
        final NodeUptimeData nodeData = data.get(event.getCreatorId());
        if (nodeData == null) {
            logger.warn(
                    EXCEPTION.getMarker(), "Node {} is not being tracked by the uptime tracker.", event.getCreatorId());
            return;
        }
        nodeData.setLastJudgeRound(event.getRoundReceived()).setLastJudgeTime(event.getConsensusTimestamp());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addNode(final long node) {
        data.put(node, new NodeUptimeData());
    }

    @Override
    public void removeNode(final long node) {
        data.remove(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeInt(data.size());
        for (final long id : data.keySet()) {
            out.writeLong(id);
            out.writeSerializable(data.get(id), false);
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
            final long nodeId = in.readLong();
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
