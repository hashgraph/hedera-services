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

import com.hedera.node.app.Hedera;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.MethodDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * An implementation of a gRPC marshaller which does nothing but pass through byte arrays as {@link
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

    DataBufferMarshaller() {}

    @Override
    public InputStream stream(@NonNull final BufferedData buffer) {
        // KnownLengthStream is a simple wrapper over the byte buffer
        Objects.requireNonNull(buffer);
        return new KnownLengthStream(buffer);
    }

    @Override
    public BufferedData parse(@NonNull final InputStream stream) {
        // NOTE: Any runtime exception thrown by this method appears correct by inspection
        // of the Google protobuf implementation.
        Objects.requireNonNull(stream);

        // Each thread has a single buffer instance that gets reused over and over.
        final var buffer = BUFFER_THREAD_LOCAL.get();
        buffer.reset();

        // We sized the buffer to be 1 byte larger than the MAX_MESSAGE_SIZE.
        // If we have filled the buffer, it means the message had too many bytes,
        // and we will therefore reject it.
        final var numBytesRead = buffer.writeBytes(stream, TOO_BIG_MESSAGE_SIZE);
        if (numBytesRead == TOO_BIG_MESSAGE_SIZE) {
            throw new RuntimeException("More than MAX_MESSAGE_SIZE (" + MAX_MESSAGE_SIZE + ") bytes read");
        }

        // We read some bytes into the buffer, so reset the position and limit accordingly to
        // prepare for reading the data
        buffer.flip();
        return buffer;
    }
}
