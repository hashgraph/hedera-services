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
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.common.Releasable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
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
public class AsyncInputStream implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(AsyncInputStream.class);

    private static final String THREAD_NAME = "async-input-stream";

    private final ReconnectConfig reconnectConfig;

    private final SerializableDataInputStream inputStream;

    // Number of expected messages to read from this input stream. If the stream is used to
    // synchronize multiple merkle sub-trees in parallel, this is the number of messages
    // accross all of them
    private final AtomicInteger anticipatedMessages;

    // Messages read from the underlying input stream so far, per merkle sub-tree
    private final Map<Integer, BlockingQueue<SelfSerializable>> viewMessages;

    /**
     * The maximum amount of time to wait when reading a message.
     */
    private final Duration pollTimeout;

    /**
     * Becomes 0 when the input thread is finished.
     */
    private final CountDownLatch finishedLatch;

    private volatile boolean alive;

    private final Map<Integer, Supplier<SelfSerializable>> messageFactories;

    private final StandardWorkGroup workGroup;

    /**
     * Create a new async input stream.
     *
     * @param inputStream    the base stream to read from
     * @param workGroup      the work group that is managing this stream's thread
     * @param config         the configuration to use
     */
    public AsyncInputStream(
            @NonNull final SerializableDataInputStream inputStream,
            @NonNull final StandardWorkGroup workGroup,
            @NonNull final ReconnectConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        this.reconnectConfig = config;
        this.inputStream = Objects.requireNonNull(inputStream, "inputStream must not be null");
        this.workGroup = Objects.requireNonNull(workGroup, "workGroup must not be null");
        this.pollTimeout = config.asyncStreamTimeout();
        this.anticipatedMessages = new AtomicInteger(0);
        this.finishedLatch = new CountDownLatch(1);
        this.alive = true;

        this.viewMessages = new ConcurrentHashMap<>();
        this.messageFactories = new ConcurrentHashMap<>();
    }

    /**
     * Start the thread that writes to the output stream.
     */
    public void start() {
        workGroup.execute(THREAD_NAME, this::run);
    }

    public void registerView(final int viewId, final Supplier<SelfSerializable> messageFactory) {
        assert !messageFactories.containsKey(viewId);
        messageFactories.put(viewId, messageFactory);
        assert !viewMessages.containsKey(viewId);
        viewMessages.put(viewId, new ArrayBlockingQueue<>(reconnectConfig.asyncStreamBufferSize()));
    }

    /**
     * Returns true if the message pump is still running or false if the message pump has terminated or will terminate.
     *
     * @return true if the message pump is still running; false if the message pump has terminated or will terminate
     */
    public boolean isAlive() {
        return alive;
    }

    /**
     * This method is run on a background thread. Continuously reads things from the stream and puts them into the
     * queue.
     */
    private void run() {
        SelfSerializable message = null;
        try {
            while (isAlive() && !Thread.currentThread().isInterrupted()) {
                final int previous =
                        anticipatedMessages.getAndUpdate((final int value) -> value == 0 ? 0 : (value - 1));

                if (previous == 0) {
                    Thread.onSpinWait();
                    continue;
                }

                final int viewId = inputStream.readInt();
                final Supplier<SelfSerializable> messageFactory = messageFactories.get(viewId);
                if (messageFactory == null) {
                    throw new MerkleSynchronizationException("Unknown view ID: " + viewId);
                }
                message = messageFactory.get();
                message.deserialize(inputStream, message.getVersion());

                final BlockingQueue<SelfSerializable> viewQueue = viewMessages.get(viewId);
                if (viewQueue == null) {
                    throw new MerkleSynchronizationException("Unknown view ID: " + viewId);
                }
                final boolean accepted = viewQueue.offer(message, pollTimeout.toMillis(), MILLISECONDS);
                if (!accepted) {
                    throw new MerkleSynchronizationException(
                            "Timed out waiting to add message to received messages queue");
                }
            }
        } catch (final IOException e) {
            throw new MerkleSynchronizationException(
                    String.format(
                            "Failed to deserialize object with class ID %d(0x%08X) (%s)",
                            message.getClassId(), message.getClassId(), message.getClass()),
                    e);
        } catch (final InterruptedException e) {
            logger.warn(RECONNECT.getMarker(), "AsyncInputStream interrupted");
            Thread.currentThread().interrupt();
        } finally {
            finishedLatch.countDown();
        }
    }

    /**
     * Inform the buffer that a message is anticipated to be received at a future time.
     */
    public void anticipateMessage() {
        anticipatedMessages.getAndIncrement();
    }

    /**
     * Get an anticipated message. Blocks until the message is ready. Object returned will be the same object passed
     * into addAnticipatedMessage, but deserialized from the stream.
     */
    @SuppressWarnings("unchecked")
    public <T extends SelfSerializable> T readAnticipatedMessage(final int viewId) throws InterruptedException {
        final BlockingQueue<SelfSerializable> viewQueue = viewMessages.get(viewId);
        if (viewQueue == null) {
            throw new MerkleSynchronizationException("Unknown view ID: " + viewId);
        }
        final SelfSerializable data = viewQueue.poll(pollTimeout.toMillis(), MILLISECONDS);
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

        return (T) data;
    }

    /**
     * This method should be called when the reader decides to stop reading from the stream (for example, if the reader
     * encounters an exception). This method ensures that any resources used by the buffered messages are released.
     */
    public void abort() {
        close();

        try {
            finishedLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (final BlockingQueue<SelfSerializable> viewQueue : viewMessages.values()) {
            while (!viewQueue.isEmpty()) {
                final SelfSerializable message = viewQueue.remove();
                if (message instanceof Releasable) {
                    ((Releasable) message).release();
                }
            }
        }
    }

    /**
     * Close this buffer and release resources.
     */
    @Override
    public void close() {
        alive = false;
    }
}
