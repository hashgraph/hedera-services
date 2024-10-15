/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.demo.platform.fs.stresstest.proto.ControlType;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

public class ControlAction implements SelfSerializable {
    private static final long CLASS_ID = 0x9d36e40b51de36fdL;

    private Instant timestamp;
    private ControlType type;

    public ControlAction() {}

    public ControlAction(final Instant timestamp, final ControlType type) {
        this.timestamp = timestamp;
        this.type = type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public ControlType getType() {
        return type;
    }

    public static ControlAction of(final ControlType type) {
        return new ControlAction(Instant.EPOCH, type);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final ControlAction that = (ControlAction) other;
        return type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("timestamp", timestamp)
                .append("type", type)
                .toString();
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeInstant(timestamp);
        out.writeInt(type.getNumber());
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        this.timestamp = in.readInstant();
        this.type = ControlType.forNumber(in.readInt());
    }

    @Override
    public int getClassVersion() {
        return 1;
    }
}
