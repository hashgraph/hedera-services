/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.state.submerkle;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class RichInstant implements Comparable<RichInstant> {
    public static final RichInstant MISSING_INSTANT = new RichInstant(0L, 0);

    private final int nanos;
    private final long seconds;

    public RichInstant() {
        nanos = 0;
        seconds = 0;
    }

    public RichInstant(final long seconds, final int nanos) {
        this.seconds = seconds;
        this.nanos = nanos;
    }

    public static RichInstant from(final SerializableDataInputStream in) throws IOException {
        return new RichInstant(in.readLong(), in.readInt());
    }

    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(seconds);
        out.writeInt(nanos);
    }

    /* --- Object --- */

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("seconds", seconds)
                .add("nanos", nanos)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || RichInstant.class != o.getClass()) {
            return false;
        }
        final var that = (RichInstant) o;
        return seconds == that.seconds && nanos == that.nanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(seconds, nanos);
    }

    /* --- Bean --- */

    public long getSeconds() {
        return seconds;
    }

    public int getNanos() {
        return nanos;
    }

    /* --- Helpers --- */

    public static RichInstant fromGrpc(final Timestamp grpc) {
        return grpc.equals(Timestamp.getDefaultInstance())
                ? MISSING_INSTANT
                : new RichInstant(grpc.getSeconds(), grpc.getNanos());
    }

    public Timestamp toGrpc() {
        return isMissing()
                ? Timestamp.getDefaultInstance()
                : Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }

    public boolean isAfter(final RichInstant other) {
        return (seconds > other.seconds) || (seconds == other.seconds && (nanos > other.nanos));
    }

    public Instant toJava() {
        return Instant.ofEpochSecond(seconds, nanos);
    }

    public static RichInstant fromJava(final Instant when) {
        return Optional.ofNullable(when)
                .map(at -> new RichInstant(at.getEpochSecond(), at.getNano()))
                .orElse(null);
    }

    public boolean isMissing() {
        return this.equals(MISSING_INSTANT);
    }

    @Override
    public int compareTo(@NonNull final RichInstant o) {
        if (o.seconds == seconds) {
            return Integer.compare(nanos, o.nanos);
        }
        return Long.compare(seconds, o.seconds);
    }
}
