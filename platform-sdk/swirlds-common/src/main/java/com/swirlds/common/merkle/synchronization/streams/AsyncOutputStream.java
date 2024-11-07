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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.common.utility.StopWatch;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * Allows a thread to asynchronously send data over a SerializableDataOutputStream.
 * </p>
 *
 * <p>
 * Only one type of message is allowed to be sent using an instance of this class. Originally this class was capable of
 * supporting arbitrary message types, but there was a significant memory footprint optimization that was made possible
 * by switching to single message type.
 * </p>
 *
 * <p>
 * This object is not thread safe. Only one thread should attempt to send data over this stream at any point in time.
 * </p>
 */
public class AsyncOutputStream {

    private static final Logger logger = LogManager.getLogger(AsyncOutputStream.class);

    /**
     * The stream which all data is written to.
     */
    private final SerializableDataOutputStream outputStream;

    /**
     * A queue that need to be written to the output stream.
     */
    private final Queue<QueueItem> streamQueue;

    /**
     * The time that has elapsed since the last flush was attempted.
     */
    private final StopWatch timeSinceLastFlush;

    /**
     * The maximum amount of time that is permitted to pass without a flush being attempted.
     */
    private final Duration flushInterval;

    /**
     * The number of messages that have been written to the stream but have not yet been flushed
     */
    private int bufferedMessageCount;

    /**
     * Using own buffer instead of BufferedOutputStream to avoid unneeded synchronization costs.
     */
    // private final ByteArrayOutputStream bufferedOut = new ByteArrayOutputStream(65536);

    /**
     * Data output stream on top of bufferedOut.
     */
    // private final DataOutputStream dataOut = new DataOutputStream(bufferedOut);

    private final StandardWorkGroup workGroup;

    /**
     * A condition to check whether it's time to terminate this output stream.
     */
    private final Supplier<Boolean> alive;

    /**
     * Constructs a new instance using the given underlying {@link SerializableDataOutputStream} and
     * {@link StandardWorkGroup}.
     *
     * @param outputStream the outputStream to which all objects are written
     * @param workGroup    the work group that should be used to execute this thread
     * @param config       the reconnect configuration
     */
    public AsyncOutputStream(
            @NonNull final SerializableDataOutputStream outputStream,
            @NonNull final StandardWorkGroup workGroup,
            @NonNull final Supplier<Boolean> alive,
            @NonNull final ReconnectConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        this.outputStream = Objects.requireNonNull(outputStream, "outputStream must not be null");
        this.workGroup = Objects.requireNonNull(workGroup, "workGroup must not be null");
        this.alive = Objects.requireNonNull(alive, "alive must not be null");
        this.streamQueue = new ConcurrentLinkedQueue<>();
        this.timeSinceLastFlush = new StopWatch();
        this.timeSinceLastFlush.start();
        this.flushInterval = config.asyncOutputStreamFlush();
    }

    /**
     * Start the thread that writes to the stream.
     */
    public void start() {
        workGroup.execute("async-output-stream", this::run);
    }

    public void run() {
        try {
            while ((alive.get() || !streamQueue.isEmpty())
                    && !Thread.currentThread().isInterrupted()) {
                flushIfRequired();
                boolean workDone = handleQueuedMessages();
                if (!workDone) {
                    workDone = flush();
                    if (!workDone) {
                        Thread.onSpinWait();
                    }
                }
            }
            // Handle remaining queued messages
            boolean wasNotEmpty = true;
            while (wasNotEmpty) {
                wasNotEmpty = handleQueuedMessages();
            }
            flush();
            try {
                // Send reconnect termination marker
                outputStream.writeInt(-1);
                outputStream.flush();
            } catch (final IOException e) {
                throw new MerkleSynchronizationException(e);
            }
        } catch (final Exception e) {
            workGroup.handleError(e);
        }
    }

    /**
     * Send a message asynchronously. Messages are guaranteed to be delivered in the order sent.
     */
    public void sendAsync(final int viewId, final SelfSerializable message) throws InterruptedException {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream(64);
        try (final SerializableDataOutputStream dout = new SerializableDataOutputStream(bout)) {
            serializeMessage(message, dout);
        } catch (final IOException e) {
            throw new MerkleSynchronizationException("Can't serialize message", e);
        }
        sendAsync(new QueueItem(viewId, bout.toByteArray()));
    }

    /**
     * Schedule to run a given runnable, when all messages currently scheduled in this async
     * stream are serialized into the underlying output stream.
     */
    public void whenCurrentMessagesProcessed(final Runnable run) throws InterruptedException {
        sendAsync(new QueueItem(run));
    }

    private void sendAsync(final QueueItem item) {
        final boolean success = streamQueue.offer(item);
        if (!success) {
            try {
                outputStream.close();
            } catch (final IOException e) {
                throw new MerkleSynchronizationException("Unable to close stream", e);
            }
            throw new MerkleSynchronizationException("Timed out waiting to send data");
        }
    }

    /**
     * Send the next message if possible.
     *
     * @return true if a message was sent.
     */
    private boolean handleQueuedMessages() {
        QueueItem item = streamQueue.poll();
        if (item == null) {
            return false;
        }
        try {
            // bufferedOut.reset();
            while (item != null) {
                if (item.toNotify() != null) {
                    assert item.messageBytes() == null;
                    item.toNotify().run();
                } else {
                    final int viewId = item.viewId();
                    final byte[] messageBytes = item.messageBytes();
                    outputStream.writeInt(viewId);
                    // dataOut.writeInt(viewId);
                    outputStream.writeInt(messageBytes.length);
                    // dataOut.writeInt(messageBytes.length);
                    outputStream.write(messageBytes);
                    // dataOut.write(messageBytes);
                    bufferedMessageCount += 1;
                    // Don't let the buffer grow too much
                    // if (bufferedOut.size() >= 256 * 1024) {
                    //     bufferedOut.writeTo(outputStream);
                    //     bufferedOut.reset();
                    // }
                }
                item = streamQueue.poll();
            }
            // bufferedOut.writeTo(outputStream);
        } catch (final IOException e) {
            throw new MerkleSynchronizationException(e);
        }
        return true;
    }

    protected void serializeMessage(final SelfSerializable message, final SerializableDataOutputStream out)
            throws IOException {
        message.serialize(out);
    }

    private boolean flush() {
        timeSinceLastFlush.reset();
        timeSinceLastFlush.start();
        if (bufferedMessageCount > 0) {
            try {
                outputStream.flush();
            } catch (final IOException e) {
                throw new MerkleSynchronizationException(e);
            }
            bufferedMessageCount = 0;
            return true;
        }
        return false;
    }

    /**
     * Flush the stream if necessary.
     */
    private void flushIfRequired() {
        if (timeSinceLastFlush.getElapsedTimeNano() > flushInterval.toNanos()) {
            flush();
        }
    }

    private record QueueItem(int viewId, byte[] messageBytes, Runnable toNotify) {
        public QueueItem(int viewId, byte[] messageBytes) {
            this(viewId, messageBytes, null);
        }

        public QueueItem(Runnable toNotify) {
            this(-1, null, toNotify);
        }
    }
}
