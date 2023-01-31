/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import io.grpc.KnownLength;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * An {@link InputStream} that implements {@link KnownLength} which allows the gRPC server to do
 * some smarter things when returning responses to clients. This stream is backed by a {@link
 * ByteBuffer}, with optimal implementations for the InputStream methods.
 */
final class KnownLengthStream extends InputStream implements KnownLength {
    private final ByteBuffer buf;

    public KnownLengthStream(ByteBuffer buf) {
        this.buf = Objects.requireNonNull(buf);
    }

    @Override
    public int read() {
        if (this.buf.remaining() == 0) {
            return -1;
        }

        return this.buf.get() & 0xFF;
    }

    @Override
    public int read(@Nonnull byte[] b) {
        int remaining = buf.remaining();
        if (remaining == 0) {
            return -1;
        }

        int numBytesToRead = Math.min(remaining, b.length);
        buf.get(b, 0, numBytesToRead);
        return numBytesToRead;
    }

    @Override
    public int read(@Nonnull byte[] b, int off, int len) {
        int remaining = buf.remaining();
        if (remaining == 0) {
            return -1;
        }

        int numBytesToRead = Math.min(remaining, len);
        buf.get(b, off, numBytesToRead);
        return numBytesToRead;
    }

    @Override
    public long skip(long n) {
        if (n <= 0) {
            return 0;
        }

        int remaining = buf.remaining();
        if (remaining == 0) {
            return 0;
        }

        int numBytesToSkip = Math.min(remaining, (int) n);
        buf.position(buf.position() + numBytesToSkip);
        return numBytesToSkip;
    }

    @Override
    public int available() {
        return buf.remaining();
    }
}
