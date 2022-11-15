package com.hedera.node.app.grpc;

import io.grpc.KnownLength;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An {@link InputStream} that implements {@link KnownLength} which allows the gRPC server
 * to do some smarter things when returning responses to clients. This stream is backed by
 * a {@link ByteBuffer}, with optimal implementations for the InputStream methods.
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
