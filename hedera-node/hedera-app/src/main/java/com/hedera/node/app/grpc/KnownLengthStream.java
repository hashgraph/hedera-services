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
package com.hedera.node.app.grpc;

import com.hedera.pbj.runtime.io.DataBuffer;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.KnownLength;
import java.io.InputStream;
import java.util.Objects;

/**
 * An {@link InputStream} that implements {@link KnownLength} which allows the gRPC server to do
 * some smarter things when returning responses to clients. This stream is backed by a {@link
 * DataBuffer}, with optimal implementations for the InputStream methods.
 */
final class KnownLengthStream extends InputStream implements KnownLength {
    private final DataBuffer buf;

    public KnownLengthStream(final DataBuffer buf) {
        this.buf = Objects.requireNonNull(buf);
    }

    @Override
    public int read() {
        if (available() == 0) {
            return -1;
        }

        return this.buf.readUnsignedByte();
    }

    @Override
    public int read(@NonNull final byte[] b) {
        final int remaining = available();
        if (remaining == 0) {
            return -1;
        }

        final int numBytesToRead = Math.min(remaining, b.length);
        buf.readBytes(b, 0, numBytesToRead);
        return numBytesToRead;
    }

    @Override
    public int read(@NonNull final byte[] b, final int off, final int len) {
        final int remaining = available();
        if (remaining == 0) {
            return -1;
        }

        final int numBytesToRead = Math.min(remaining, len);
        buf.readBytes(b, off, numBytesToRead);
        return numBytesToRead;
    }

    @Override
    public long skip(final long n) {
        if (n <= 0) {
            return 0;
        }

        final int remaining = available();
        if (remaining == 0) {
            return 0;
        }

        final int numBytesToSkip = Math.min(remaining, (int) n);
        buf.skip(numBytesToSkip);
        return numBytesToSkip;
    }

    @Override
    public int available() {
        return (int) buf.getRemaining();
    }
}
