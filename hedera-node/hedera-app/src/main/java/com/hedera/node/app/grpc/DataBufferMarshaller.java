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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.Hedera;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.MethodDescriptor;
import java.io.InputStream;

/**
 * A thread-safe implementation of a gRPC marshaller which does nothing but pass through byte arrays as {@link
 * BufferedData}s. A single implementation of this class is designed to be used by multiple threads,
 * including by multiple app instances within a single JVM!
 */
/*@ThreadSafe*/
final class DataBufferMarshaller implements MethodDescriptor.Marshaller<BufferedData> {
    // NOTE: This needs to come from config, but because of the thread local, has to be
    //       static. See Issue #4294
    private static final int MAX_MESSAGE_SIZE = Hedera.MAX_SIGNED_TXN_SIZE;
    private static final int TOO_BIG_MESSAGE_SIZE = MAX_MESSAGE_SIZE + 1;

    /**
     * Per-thread shared ByteBuffer for reading. We store these in a thread local, because we do not
     * have control over the thread pool used by the underlying gRPC server.
     */
    private static final ThreadLocal<BufferedData> BUFFER_THREAD_LOCAL =
            ThreadLocal.withInitial(() -> BufferedData.allocate(TOO_BIG_MESSAGE_SIZE));

    /** Constructs a new {@link DataBufferMarshaller}. Only called by {@link GrpcServiceBuilder}. */
    DataBufferMarshaller() {}

    /** {@inheritDoc} */
    @Override
    @NonNull
    public InputStream stream(@NonNull final BufferedData buffer) {
        // KnownLengthStream is a simple wrapper over the byte buffer. We use it because it offers a
        // better performance profile with the gRPC server than a normal InputBuffer.
        return new KnownLengthStream(buffer);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public BufferedData parse(@NonNull final InputStream stream) {
        // NOTE: Any runtime exception thrown by this method appears correct by inspection
        // of the Google protobuf implementation.
        requireNonNull(stream);

        // Each thread has a single buffer instance that gets reused over and over.
        final var buffer = BUFFER_THREAD_LOCAL.get();
        buffer.reset();

        // We sized the buffer to be 1 byte larger than the MAX_MESSAGE_SIZE.
        // If we have filled the buffer, it means the message had too many bytes,
        // and we will therefore reject it in MethodBase. We reject it there instead of here
        // because if we throw an exception here, Helidon will log a stack trace, which we don't
        // want to do for bad input from the user. Also note that if the user sent us way too many
        // bytes, this method will only read up to TOO_BIG_MESSAGE_SIZE, so there is no risk of
        // the user overwhelming the server with a huge message.
        buffer.writeBytes(stream, TOO_BIG_MESSAGE_SIZE);

        // We read some bytes into the buffer, so reset the position and limit accordingly to
        // prepare for reading the data
        buffer.flip();
        return buffer;
    }
}
