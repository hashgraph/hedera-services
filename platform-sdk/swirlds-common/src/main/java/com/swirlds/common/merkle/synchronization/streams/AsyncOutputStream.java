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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.common.utility.StopWatch;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
 *
 * @param <T> the type of the message to send
 */
public class AsyncOutputStream<T extends SelfSerializable> implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(AsyncOutputStream.class);

    /**
     * The stream which all data is written to.
     */
    private final SerializableDataOutputStream outputStream;

    /**
     * A queue of messages that need to be written to the output stream.
     */
    private final BlockingQueue<T> outgoingMessages;

    /**
     * If this amount of time passes in between operations then flush the output stream. An operation is defined as
     * either writing data to the stream or flushing the stream.
     */
    private final StopWatch timeSinceLastOperation;

    /**
     * If this amount of time passes after an operation without another operation happening then flush the stream.
     */
    private final Duration operationInterval;

    /**
     * If this becomes false then this object's worker thread will stop transmitting messages.
     */
    private volatile boolean alive;

    /**
     * The maximum amount of time to wait when writing a message.
     */
    private final Duration timeout;

    private final StandardWorkGroup workGroup;

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
            @NonNull final ReconnectConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        this.outputStream = Objects.requireNonNull(outputStream, "outputStream must not be null");
        this.workGroup = Objects.requireNonNull(workGroup, "workGroup must not be null");
        this.outgoingMessages = new LinkedBlockingQueue<>(config.asyncStreamBufferSize());
        this.alive = true;
        this.timeSinceLastOperation = new StopWatch();
        this.timeSinceLastOperation.start();
        this.operationInterval = config.asyncOutputStreamFlush();
        this.timeout = config.asyncStreamTimeout();
    }

    /**
     * Start the thread that reads from the stream.
     */
    public void start() {
        workGroup.execute("async-output-stream", this::run);
    }

    /**
     * Returns true if the message pump is still running or false if the message pump has terminated or will terminate.
     *
     * @return true if the message pump is still running; false if the message pump has terminated or will terminate
     */
    public boolean isAlive() {
        return alive;
    }

    protected SerializableDataOutputStream getOutputStream() {
        return outputStream;
    }

    /**
     * This is exposed to allow test classes to simulate latency.
     */
    protected BlockingQueue<T> getOutgoingMessages() {
        return outgoingMessages;
    }

    public void run() {
        while ((isAlive() || !outgoingMessages.isEmpty())
                && !Thread.currentThread().isInterrupted()) {
            handleNextMessage();
            flushIfRequired();
        }
        flush();
    }

    /**
     * Send a message asynchronously. Messages are guaranteed to be delivered in the order sent.
     */
    public void sendAsync(@NonNull final T message) throws InterruptedException {
        if (!isAlive()) {
            throw new MerkleSynchronizationException("Messages can not be sent after close has been called.");
        }

        final boolean success = outgoingMessages.offer(message, timeout.toMillis(), TimeUnit.MILLISECONDS);

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
     * Close this buffer and release resources. If there are still messages awaiting transmission then resources will
     * not be immediately freed.
     */
    @Override
    public void close() {
        alive = false;
    }

    /**
     * Send the next message if possible. Will block for a short time if there is no message to send, but will not block
     * indefinitely.
     */
    private void handleNextMessage() {
        try {
            final T message = outgoingMessages.poll();
            if (message == null) {
                Thread.yield();
                return;
            }
            serializeMessage(message);
            resetTimer();
        } catch (final IOException e) {
            throw new MerkleSynchronizationException(e);
        }
    }

    /**
     * Serialize the given message to the output stream.
     *
     * @param message the message to serialize
     */
    protected void serializeMessage(@NonNull final T message) throws IOException {
        message.serialize(outputStream);
    }

    /**
     * Calling this message resets the timer that determines when the stream should be flushed. This should be called
     * after every operation (i.e. a message is sent or the stream is flushed).
     */
    private void resetTimer() {
        timeSinceLastOperation.reset();
        timeSinceLastOperation.start();
    }

    /**
     * Flush the stream if necessary.
     */
    private void flushIfRequired() {
        if (timeSinceLastOperation.getElapsedTimeNano() > operationInterval.toNanos()) {
            flush();
        }
    }

    /**
     * Flush the stream.
     */
    private void flush() {
        resetTimer();
        try {
            outputStream.flush();
        } catch (final IOException e) {
            throw new MerkleSynchronizationException(e);
        }
    }
}
