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

import io.grpc.MethodDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An implementation of a gRPC marshaller which does nothing but pass through byte arrays. A single
 * implementation of this class is designed to be used by multiple threads, including by multiple
 * app instances within a single JVM!
 */
@ThreadSafe
final class NoopMarshaller implements MethodDescriptor.Marshaller<ByteBuffer> {
    // NOTE: This needs to come from config, but because of the thread local, has to be
    //       static. See Issue #4294
    private static final int MAX_MESSAGE_SIZE = 1024 * 6; // 6k

    /**
     * Per-thread shared ByteBuffer for reading. We store these in a thread local, because we do not
     * have control over the thread pool used by the underlying gRPC server.
     */
    private static final ThreadLocal<ByteBuffer> BUFFER_THREAD_LOCAL =
            ThreadLocal.withInitial(() -> ByteBuffer.allocate(MAX_MESSAGE_SIZE + 1));

    NoopMarshaller() {}

    @Override
    public InputStream stream(@Nonnull final ByteBuffer buffer) {
        // KnownLengthStream is a simple wrapper over the byte buffer
        Objects.requireNonNull(buffer);
        return new KnownLengthStream(buffer);
    }

    @Override
    public ByteBuffer parse(@Nonnull final InputStream stream) {
        Objects.requireNonNull(stream);

        try {
            final var buffer = BUFFER_THREAD_LOCAL.get();
            buffer.clear();
            int numBytesRead;
            while ((numBytesRead =
                            stream.read(buffer.array(), buffer.position(), buffer.remaining()))
                    != -1) {
                buffer.position(buffer.position() + numBytesRead);
                if (buffer.remaining() == 0) {
                    // We sized the buffer to be 1 byte larger than the MAX_MESSAGE_SIZE.
                    // If we have filled the buffer, it means the message had too many bytes,
                    // and we will therefore reject it.
                    throw new RuntimeException(
                            "More than MAX_MESSAGE_SIZE (" + MAX_MESSAGE_SIZE + ") bytes read");
                }
            }
            buffer.flip(); // Prepare for reading
            return buffer;
        } catch (IOException e) {
            // This appears correct after looking at Google's implementation of this method
            // in protobuf-lite
            throw new RuntimeException(e);
        }
    }
}
