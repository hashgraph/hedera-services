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

import static com.swirlds.platform.uptime.UptimeDataImpl.NO_ROUND;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;

/**
 * Uptime data about a particular node.
 */
class NodeUptimeData implements FastCopyable, SelfSerializable {

    private long lastEventRound = NO_ROUND;
    private Instant lastEventTime;
    private long lastJudgeRound = NO_ROUND;
    private Instant lastJudgeTime;

    private static final long CLASS_ID = 0xa0893b5c8dc333d4L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * Zero-arg constructor required by SelfSerializable interface.
     */
    public NodeUptimeData() {}

    /**
     * Copy constructor.
     *
     * @param that the object to copy
     */
    private NodeUptimeData(@NonNull final NodeUptimeData that) {
        this.lastEventRound = that.lastEventRound;
        this.lastEventTime = that.lastEventTime;
        this.lastJudgeRound = that.lastJudgeRound;
        this.lastJudgeTime = that.lastJudgeTime;
    }

    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeLong(lastEventRound);
        out.writeInstant(lastEventTime);
        out.writeLong(lastJudgeRound);
        out.writeInstant(lastJudgeTime);
    }

    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        lastEventRound = in.readLong();
        lastEventTime = in.readInstant();
        lastJudgeRound = in.readLong();
        lastJudgeTime = in.readInstant();
    }

    /**
     * Get the round number of the most recently observed consensus event.
     */
    public long getLastEventRound() {
        return lastEventRound;
    }

    /**
     * Set the round number of the most recently observed consensus event.
     *
     * @param lastEventRound the round number of the most recently observed consensus event
     * @return this
     */
    @NonNull
    public NodeUptimeData setLastEventRound(final long lastEventRound) {
        this.lastEventRound = lastEventRound;
        return this;
    }

    /**
     * Get the time of the most recently observed consensus event.
     */
    @Nullable
    public Instant getLastEventTime() {
        return lastEventTime;
    }

    /**
     * Set the time of the most recently observed consensus event.
     *
     * @param lastEventTime the time of the most recently observed consensus event
     * @return this
     */
    @NonNull
    public NodeUptimeData setLastEventTime(@Nullable final Instant lastEventTime) {
        this.lastEventTime = lastEventTime;
        return this;
    }

    /**
     * Get the round number of the most recently observed judge event.
     */
    public long getLastJudgeRound() {
        return lastJudgeRound;
    }

    /**
     * Set the round number of the most recently observed judge event.
     *
     * @param lastJudgeRound the round number of the most recently observed judge event
     * @return this
     */
    @NonNull
    public NodeUptimeData setLastJudgeRound(final long lastJudgeRound) {
        this.lastJudgeRound = lastJudgeRound;
        return this;
    }

    /**
     * Get the time of the most recently observed judge event.
     */
    @Nullable
    public Instant getLastJudgeTime() {
        return lastJudgeTime;
    }

    /**
     * Set the time of the most recently observed judge event.
     *
     * @param lastJudgeTime the time of the most recently observed judge event
     * @return this
     */
    @NonNull
    public NodeUptimeData setLastJudgeTime(@Nullable final Instant lastJudgeTime) {
        this.lastJudgeTime = lastJudgeTime;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public NodeUptimeData copy() {
        return new NodeUptimeData(this);
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
}
