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
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
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

    // Messages read from the underlying input stream so far, per merkle sub-tree
    private final Map<Integer, BlockingQueue<SelfSerializable>> viewQueues;

    private final Queue<SelfSerializable> sharedQueue;

    /**
     * The maximum amount of time to wait when reading a message.
     */
    private final Duration pollTimeout;

    /**
     * Becomes 0 when the input thread is finished.
     */
    private final CountDownLatch finishedLatch;

    private final AtomicBoolean alive = new AtomicBoolean(true);

    private final Function<Integer, SelfSerializable> messagesFactory;

    private final StandardWorkGroup workGroup;

    private final int sharedQueueSizeThreshold;

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
            @NonNull final Function<Integer, SelfSerializable> messagesFactory,
            @NonNull final ReconnectConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        this.reconnectConfig = config;
        this.inputStream = Objects.requireNonNull(inputStream, "inputStream must not be null");
        this.workGroup = Objects.requireNonNull(workGroup, "workGroup must not be null");
        this.messagesFactory = Objects.requireNonNull(messagesFactory, "Messages factory must not be null");
        this.pollTimeout = config.asyncStreamTimeout();
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
        SelfSerializable message = null;
        try {
            while (alive.get() && !Thread.currentThread().isInterrupted()) {
                message = null;
                final int viewId = inputStream.readInt();
                if (viewId < 0) {
                    logger.info(RECONNECT.getMarker(), "Async input stream is done");
                    alive.set(false);
                    break;
                }
                message = messagesFactory.apply(viewId);
                if (message == null) {
                    throw new MerkleSynchronizationException(
                            "Cannot deserialize a message, unknown view ID: " + viewId);
                }
                message.deserialize(inputStream, message.getVersion());

                final BlockingQueue<SelfSerializable> viewQueue = viewQueues.get(viewId);
                if (viewQueue != null) {
                    final boolean accepted = viewQueue.offer(message, pollTimeout.toMillis(), MILLISECONDS);
                    if (!accepted) {
                        throw new MerkleSynchronizationException(
                                "Timed out waiting to add message to received messages queue");
                    }
                } else {
                    sharedQueue.add(message);
                    // Slow down reading from the stream, if handling threads can't keep up
                    if (sharedQueue.size() > sharedQueueSizeThreshold) {
                        Thread.sleep(0, 1);
                    }
                }
            }
        } catch (final IOException e) {
            final String exceptionMessage = message == null
                    ? "Failed to deserialize a message"
                    : String.format(
                            "Failed to deserialize object with class ID %d(0x%08X) (%s)",
                            message.getClassId(), message.getClassId(), message.getClass());
            workGroup.handleError(new MerkleSynchronizationException(exceptionMessage, e));
        } catch (final InterruptedException e) {
            logger.warn(RECONNECT.getMarker(), "AsyncInputStream interrupted");
            workGroup.handleError(e);
        } finally {
            finishedLatch.countDown();
        }
    }

    public void setNeedsDedicatedQueue(final int viewId) {
        assert !viewQueues.containsKey(viewId) : "View " + viewId + " is already registered in this async input stream";
        viewQueues.put(viewId, new ArrayBlockingQueue<>(reconnectConfig.asyncStreamBufferSize()));
    }

    public boolean isAlive() {
        return alive.get();
    }

    @SuppressWarnings("unchecked")
    public <T extends SelfSerializable> T readAnticipatedMessage() {
        return (T) sharedQueue.poll();
    }

    /**
     * Get an anticipated message. Blocks until the message is ready. Object returned will be the same object passed
     * into addAnticipatedMessage, but deserialized from the stream.
     */
    @SuppressWarnings("unchecked")
    public <T extends SelfSerializable> T readAnticipatedMessage(final int viewId) throws InterruptedException {
        final BlockingQueue<SelfSerializable> viewQueue = viewQueues.get(viewId);
        assert viewQueue != null;
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

        abortQueue(sharedQueue);
        for (final BlockingQueue<SelfSerializable> queue : viewQueues.values()) {
            abortQueue(queue);
        }
    }

    private void abortQueue(final Queue<SelfSerializable> queue) {
        while (!queue.isEmpty()) {
            final SelfSerializable message = queue.remove();
            if (message instanceof Releasable) {
                ((Releasable) message).release();
            }
        }
    }

    /**
     * Close this buffer and release resources.
     */
    @Override
    public void close() {
        alive.set(false);
    }

    public void waitForCompletion() throws InterruptedException {
        finishedLatch.await();
    }
}
