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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Instant;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A merkle leaf that contains the uptime data for the network.
 */
public class UptimeData extends PartialMerkleLeaf implements MerkleLeaf { // TODO test this class

    private static final long CLASS_ID = 0x1f13fa8c89b27a8cL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private final SortedMap<Long, Instant> lastConsensusEventTimes = new TreeMap<>();
    private final SortedMap<Long, Instant> lastJudgeTimes = new TreeMap<>();

    /**
     * Zero arg constructor required by serialization engine.
     */
    public UptimeData() {}

    /**
     * Copy constructor.
     */
    private UptimeData(@NonNull final UptimeData other) {
        lastConsensusEventTimes.putAll(other.lastConsensusEventTimes);
        lastJudgeTimes.putAll(other.lastJudgeTimes);
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
     * Get a map from node ID to the consensus time when the last consensus event was received from the given node.
     * Modifying this map is allowed by the caller, and will update the map stored in this object.
     */
    public SortedMap<Long, Instant> getLastConsensusEventTimes() {
        return lastConsensusEventTimes;
    }

    /**
     * Get a map from node ID to the consensus time when the last judge event was observed from the given node.
     * Modifying this map is allowed by the caller, and will update the map stored in this object.
     */
    public SortedMap<Long, Instant> getLastJudgeTimes() {
        return lastJudgeTimes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeInt(lastConsensusEventTimes.size());
        for (final long id : lastConsensusEventTimes.keySet()) {
            out.writeLong(id);
            out.writeInstant(lastConsensusEventTimes.get(id));
        }
        out.writeInt(lastJudgeTimes.size());
        for (final long id : lastJudgeTimes.keySet()) {
            out.writeLong(id);
            out.writeInstant(lastJudgeTimes.get(id));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        final int lastConsensusEventTimesSize = in.readInt();
        for (int i = 0; i < lastConsensusEventTimesSize; i++) {
            final long id = in.readLong();
            final Instant time = in.readInstant();
            lastConsensusEventTimes.put(id, time);
        }
        final int lastJudgeTimesSize = in.readInt();
        for (int i = 0; i < lastJudgeTimesSize; i++) {
            final long id = in.readLong();
            final Instant time = in.readInstant();
            lastJudgeTimes.put(id, time);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public UptimeData copy() {
        return new UptimeData(this);
    }
}
