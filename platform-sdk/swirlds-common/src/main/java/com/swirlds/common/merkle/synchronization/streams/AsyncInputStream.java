/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.LogMarker.RECONNECT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.common.Releasable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettingsFactory;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
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
 * Only one type of message is allowed to be read using an instance of this class. Originally this class was capable
 * of supporting arbitrary message types, but there was a significant memory footprint optimization that was made
 * possible by switching to single message type.
 * </p>
 *
 * <p>
 * This object is not thread safe. Only one thread should attempt to read data from stream at any point in time.
 * </p>
 */
public class AsyncInputStream<T extends SelfSerializable> implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(AsyncInputStream.class);

    private static final String THREAD_NAME = "async-input-stream";

    private final SerializableDataInputStream inputStream;

    private final AtomicInteger anticipatedMessages;
    private final BlockingQueue<SelfSerializable> receivedMessages;

    /**
     * The maximum amount of time to wait when reading a message.
     */
    private final int pollTimeoutMs;

    /**
     * Becomes 0 when the input thread is finished.
     */
    private final CountDownLatch finishedLatch;

    private volatile boolean alive;

    private final Supplier<T> messageFactory;

    private final StandardWorkGroup workGroup;

    /**
     * Create a new async input stream.
     *
     * @param inputStream
     * 		the base stream to read from
     * @param workGroup
     * 		the work group that is managing this stream's thread
     * @param messageFactory
     * 		this function constructs new message objects. These messages objects
     * 		are then used to read data via {@link SelfSerializable#deserialize(SerializableDataInputStream, int)}.
     */
    public AsyncInputStream(
            final SerializableDataInputStream inputStream,
            final StandardWorkGroup workGroup,
            final Supplier<T> messageFactory) {

        final ReconnectSettings settings = ReconnectSettingsFactory.get();

        this.inputStream = inputStream;
        this.workGroup = workGroup;
        this.messageFactory = messageFactory;
        this.pollTimeoutMs = settings.getAsyncStreamTimeoutMilliseconds();
        this.anticipatedMessages = new AtomicInteger(0);
        this.receivedMessages = new LinkedBlockingQueue<>(settings.getAsyncStreamBufferSize());
        this.finishedLatch = new CountDownLatch(1);
        this.alive = true;
    }

    /**
     * Start the thread that writes to the output stream.
     */
    public void start() {
        workGroup.execute(THREAD_NAME, this::run);
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
     * Get the number of messages that this stream is still expecting to receive.
     */
    public long getTotalAnticipatedMessages() {
        return anticipatedMessages.get();
    }

    /**
     * This method is run on a background thread. Continuously reads things from the stream and puts
     * them into the queue.
     */
    private void run() {
        T message = null;
        try {
            while (isAlive() && !Thread.currentThread().isInterrupted()) {
                final int previous =
                        anticipatedMessages.getAndUpdate((final int value) -> value == 0 ? 0 : (value - 1));

                if (previous == 0) {
                    MILLISECONDS.sleep(1);
                    continue;
                }

                message = messageFactory.get();
                message.deserialize(inputStream, message.getVersion());
                receivedMessages.put(message);
            }
        } catch (final IOException e) {
            throw new MerkleSynchronizationException(
                    String.format(
                            "Failed to deserialize object with class ID %d(0x%08X) (%s)",
                            message.getClassId(),
                            message.getClassId(),
                            message.getClass().toString()),
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
     * Get an anticipated message. Blocks until the message is ready. Object returned will be
     * the same object passed into addAnticipatedMessage, but deserialized from the stream.
     */
    public T readAnticipatedMessage() throws InterruptedException {
        return asyncRead();
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

        while (!receivedMessages.isEmpty()) {
            SelfSerializable message = receivedMessages.remove();
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
        alive = false;
    }

    /**
     * Read a message. Will throw an exception if time equal to {@link #pollTimeoutMs} passes without a
     * message becoming available.
     */
    @SuppressWarnings("unchecked")
    private T asyncRead() throws InterruptedException {
        T data = (T) receivedMessages.poll(pollTimeoutMs, MILLISECONDS);
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

        return data;
    }
}
