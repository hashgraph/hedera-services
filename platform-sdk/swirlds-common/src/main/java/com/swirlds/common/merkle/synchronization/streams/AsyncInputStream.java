/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.synchronization.streams;

import static com.swirlds.logging.legacy.LogMarker.RECONNECT;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * Allows a thread to asynchronously read data from a SerializableDataInputStream.
 * </p>
 *
 * <p>
 * Only one type of message is allowed to be read using an instance of this class. Originally this class was capable of
 * supporting arbitrary message types, but there was a significant memory footprint optimization that was made possible
 * by switching to single message type.
 * </p>
 *
 * <p>
 * This object is not thread safe. Only one thread should attempt to read data from stream at any point in time.
 * </p>
 */
public class AsyncInputStream {

    private static final Logger logger = LogManager.getLogger(AsyncInputStream.class);

    private static final String THREAD_NAME = "async-input-stream";

    private final SerializableDataInputStream inputStream;

    // Reading directly from inputStream is expensive, since every read results in
    // taking a lock. To minimize the number of reads, let's use our own buffer
    private final byte[] readBuffer = new byte[256 * 1024];

    // A position in readBuffer to read data next. If the position is equal readLimit
    // below, there is no data available in the buffer, and a read from inputStream
    // is required
    private int readPos = 0;

    // Read buffer may not be filled up completely, it really depends on how much
    // data is available in the socket. This limit indicates the last position in
    // the buffer that holds data read from inputStream
    private int readLimit = 0;

    // A byte buffer on top of readBuffer. Used to read ints and bytes from the array
    private final ByteBuffer inputStreamAccessor = ByteBuffer.wrap(readBuffer);

    // Messages read from the underlying input stream so far, per merkle sub-tree
    public final Map<Integer, Queue<byte[]>> viewQueues;

    private final Queue<SharedQueueItem> sharedQueue;

    private final Set<Integer> useSharedQueue = ConcurrentHashMap.newKeySet();

    // Checking queue size on every received message may be expensive. Instead, track the
    // size manually using an atomic
    private final AtomicInteger sharedQueueSize = new AtomicInteger(0);

    /**
     * The maximum amount of time to wait when reading a message.
     */
    private final Duration pollTimeout;

    /**
     * Becomes 0 when the input thread is finished.
     */
    private final CountDownLatch finishedLatch;

    private final AtomicBoolean alive = new AtomicBoolean(true);

    private final StandardWorkGroup workGroup;

    private final int sharedQueueSizeThreshold;

    /**
     * Create a new async input stream.
     *
     * @param inputStream     the base stream to read from
     * @param workGroup       the work group that is managing this stream's thread
     * @param reconnectConfig the configuration to use
     */
    public AsyncInputStream(
            @NonNull final SerializableDataInputStream inputStream,
            @NonNull final StandardWorkGroup workGroup,
            @NonNull final ReconnectConfig reconnectConfig) {
        Objects.requireNonNull(reconnectConfig, "Reconnect config must not be null");

        this.inputStream = Objects.requireNonNull(inputStream, "inputStream must not be null");
        this.workGroup = Objects.requireNonNull(workGroup, "workGroup must not be null");
        this.pollTimeout = reconnectConfig.asyncStreamTimeout();
        this.finishedLatch = new CountDownLatch(1);

        this.sharedQueue = new ConcurrentLinkedQueue<>();
        this.viewQueues = new ConcurrentHashMap<>();

        this.sharedQueueSizeThreshold = reconnectConfig.asyncStreamBufferSize() * reconnectConfig.maxParallelSubtrees();
    }

    /**
     * Start the thread that writes to the output stream.
     */
    public void start() {
        workGroup.execute(THREAD_NAME, this::run);
    }

    /**
     * This method is run on a background thread. Continuously reads things from the stream and puts them into the
     * queue.
     */
    private void run() {
        logger.info(RECONNECT.getMarker(), this.toString() + " start run()");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final int viewId = readInt();
                if (viewId < 0) {
                    logger.info(RECONNECT.getMarker(), "Async input stream is done");
                    alive.set(false);
                    break;
                }
                final int len = readInt();
                final byte[] messageBytes = new byte[len];
                readBytes(messageBytes);

                if (useSharedQueue.contains(viewId)) {
                    assert !viewQueues.containsKey(viewId);
                    sharedQueue.add(new SharedQueueItem(viewId, messageBytes));
                    // Slow down reading from the stream, if handling threads can't keep up
                    if (sharedQueueSize.incrementAndGet() > sharedQueueSizeThreshold) {
                        Thread.onSpinWait();
                    }
                } else {
                    final Queue<byte[]> viewQueue =
                            viewQueues.computeIfAbsent(viewId, t -> new ConcurrentLinkedQueue<>());
                    final boolean accepted = viewQueue.add(messageBytes);
                    if (!accepted) {
                        throw new MerkleSynchronizationException(
                                "Timed out waiting to add message to received messages queue");
                    }
                }
            }
        } catch (final IOException e) {
            workGroup.handleError(e);
        } finally {
            finishedLatch.countDown();
        }
        logger.info(RECONNECT.getMarker(), this.toString() + " finish run()");
    }

    /**
     * Makes sure that at least {@code bytes} bytes are available to use in the read
     * buffer. It may require a read from the underlying input stream. This method may
     * update both {@link #readPos} and {@link #readLimit}.
     *
     * @param bytes number of bytes to make available in the read buffer
     * @throws IOException if an I/O error occurs
     */
    private void ensureAvailable(final int bytes) throws IOException {
        if (bytes > readBuffer.length) {
            throw new IllegalStateException("Read buffer is too small to read " + bytes + " bytes");
        }
        final int currentlyAvailable = readLimit - readPos;
        if (currentlyAvailable >= bytes) {
            return;
        }
        if (readPos + bytes > readBuffer.length) {
            System.arraycopy(readBuffer, readPos, readBuffer, 0, currentlyAvailable);
            readPos = 0;
            readLimit = currentlyAvailable;
        }
        while (readLimit - readPos < bytes) {
            // Read at least the requested number of bytes
            int toRead = bytes - (readLimit - readPos);
            // If more bytes are inputStream.available(), read all of them, but no more than to the
            // end of the read buffer
            final int avail = inputStream.available();
            if (avail > toRead) {
                toRead = Math.min(avail, readBuffer.length - readLimit);
            }
            readLimit += inputStream.read(readBuffer, readLimit, toRead);
        }
        assert readPos + bytes <= readBuffer.length;
    }

    private int readInt() throws IOException {
        ensureAvailable(Integer.BYTES);
        final int result = inputStreamAccessor.getInt(readPos);
        readPos += Integer.BYTES;
        return result;
    }

    private void readBytes(final byte[] dst) throws IOException {
        ensureAvailable(dst.length);
        inputStreamAccessor.get(readPos, dst);
        readPos += dst.length;
    }

    public void setNeedsSharedQueue(final int viewId) {
        if (viewQueues.containsKey(viewId)) {
            throw new MerkleSynchronizationException("Can't switch view=" + viewId + " to use shared queue, as"
                    + " a dedicated queue has been already created for it, size="
                    + viewQueues.get(viewId).size());
        }
        viewQueues.remove(viewId);
        useSharedQueue.add(viewId);
    }

    public boolean isAlive() {
        return alive.get();
    }

    private <T extends SelfSerializable> T deserializeMessage(final byte[] messageBytes, final T message)
            throws IOException {
        try (final ByteArrayInputStream bin = new ByteArrayInputStream(messageBytes);
                final SerializableDataInputStream in = new SerializableDataInputStream(bin)) {
            message.deserialize(in, message.getVersion());
        }
        return message;
    }

    public <T extends SelfSerializable> T readAnticipatedMessage(final Function<Integer, T> messageFactory)
            throws IOException {
        final SharedQueueItem item = sharedQueue.poll();
        if (item != null) {
            sharedQueueSize.decrementAndGet();
            final int viewId = item.viewId();
            final byte[] messageBytes = item.messageBytes();
            return deserializeMessage(messageBytes, messageFactory.apply(viewId));
        }
        return null;
    }

    /**
     * Get an anticipated message. Blocks until the message is ready. Object returned will be the same object passed
     * into addAnticipatedMessage, but deserialized from the stream.
     */
    public <T extends SelfSerializable> T readAnticipatedMessage(final int viewId, final Supplier<T> messageFactory)
            throws IOException, InterruptedException {
        final Queue<byte[]> viewQueue = viewQueues.computeIfAbsent(viewId, t -> new ConcurrentLinkedQueue<>());
        // Emulate blocking queue poll with a timeout
        byte[] data = viewQueue.poll();
        if (data == null) {
            final long start = System.currentTimeMillis();
            final Thread currentThread = Thread.currentThread();
            do {
                data = viewQueue.poll();
                if (data != null) {
                    break;
                }
                Thread.sleep(0, 1);
            } while ((System.currentTimeMillis() - start < pollTimeout.toMillis()) && !currentThread.isInterrupted());
        }
        if (data == null) {
            try {
                // An interrupt may not stop the thread if the thread is blocked on a stream read operation.
                // The only way to ensure that the stream is closed is to close the stream.
                inputStream.close();
            } catch (IOException e) {
                throw new MerkleSynchronizationException("Unable to close stream", e);
            }
            throw new MerkleSynchronizationException("Timed out waiting for data");
        }
        return deserializeMessage(data, messageFactory.get());
    }

    /**
     * This method should be called when the reader decides to stop reading from the stream (for example, if the reader
     * encounters an exception). This method ensures that any resources used by the buffered messages are released.
     */
    public void abort() {
        alive.set(false);
        try {
            finishedLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static record SharedQueueItem(int viewId, byte[] messageBytes) {}
}
