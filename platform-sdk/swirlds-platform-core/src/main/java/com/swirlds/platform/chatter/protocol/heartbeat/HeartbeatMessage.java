/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.chatter.protocol.heartbeat;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

/**
 * A message that can represent either a heartbeat request or a response
 */
public final class HeartbeatMessage implements SelfSerializable {
    private static final long CLASS_ID = 0x26bc1f207b414e4dL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private long heartbeatId;
    private boolean response;

    public HeartbeatMessage() {}

    private HeartbeatMessage(final long heartbeatId, final boolean response) {
        this.heartbeatId = heartbeatId;
        this.response = response;
    }

    /**
     * Create a new heartbeat request
     *
     * @param heartbeatId
     * 		the ID of the request
     * @return the request message
     */
    public static HeartbeatMessage request(final long heartbeatId) {
        return new HeartbeatMessage(heartbeatId, false);
    }

    /**
     * Create a new heartbeat response
     *
     * @param heartbeatId
     * 		the ID of the response
     * @return the response message
     */
    public static HeartbeatMessage response(final long heartbeatId) {
        return new HeartbeatMessage(heartbeatId, true);
    }

    /**
     * @return the ID of the heartbeat
     */
    public long getHeartbeatId() {
        return heartbeatId;
    }

    /**
     * @return true if it's a response, false if it's a request
     */
    public boolean isResponse() {
        return response;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(heartbeatId);
        out.writeBoolean(response);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        heartbeatId = in.readLong();
        response = in.readBoolean();
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }
}
