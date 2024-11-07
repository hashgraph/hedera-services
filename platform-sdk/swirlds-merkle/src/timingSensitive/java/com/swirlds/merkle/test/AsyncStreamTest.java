/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.test;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig_;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.test.fixtures.junit.tags.TestComponentTags;
import com.swirlds.common.test.fixtures.merkle.dummy.BlockingInputStream;
import com.swirlds.common.test.fixtures.merkle.dummy.BlockingOutputStream;
import com.swirlds.common.test.fixtures.merkle.util.PairedStreams;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Async Stream Test")
class AsyncStreamTest {

    private final Configuration configuration = new TestConfigBuilder()
            .withValue("reconnect.asyncOutputStream", 10000) // FUTURE: Looks like that property is not defined
            .withValue(ReconnectConfig_.ASYNC_STREAM_BUFFER_SIZE, 100)
            .withValue(ReconnectConfig_.ASYNC_OUTPUT_STREAM_FLUSH, "50ms")
            .getOrCreateConfig();
    private final ReconnectConfig reconnectConfig = configuration.getConfigData(ReconnectConfig.class);

    @Test
    @Tag(TestComponentTags.RECONNECT)
    @DisplayName("Basic Operation")
    void basicOperation() throws IOException, InterruptedException {
        try (final PairedStreams streams = new PairedStreams()) {
            final StandardWorkGroup workGroup = new StandardWorkGroup(getStaticThreadManager(), "test", null);

            final int viewId = 11;

            final AsyncInputStream in = new AsyncInputStream(streams.getTeacherInput(), workGroup, reconnectConfig);
            final AsyncOutputStream out =
                    new AsyncOutputStream(streams.getLearnerOutput(), workGroup, in::isAlive, reconnectConfig);

            in.start();
            out.start();

            final int count = 100;

            for (int i = 0; i < count; i++) {
                out.sendAsync(viewId, new SerializableLong(i));
                final SerializableLong message = in.readAnticipatedMessage(viewId, SerializableLong::new);
                assertEquals(i, message.getValue(), "message should match the value that was serialized");
            }

            // Send reconnect completion marker
            streams.getLearnerOutput().writeInt(-1);
            streams.getLearnerOutput().flush();

            workGroup.waitForTermination();
        }
    }

    @Test
    @Tag(TestComponentTags.RECONNECT)
    @DisplayName("Pre-Anticipation")
    void preAnticipation() throws IOException, InterruptedException {
        try (final PairedStreams streams = new PairedStreams()) {
            final StandardWorkGroup workGroup = new StandardWorkGroup(getStaticThreadManager(), "test", null);

            final int viewId = 12;

            final AsyncInputStream in = new AsyncInputStream(streams.getTeacherInput(), workGroup, reconnectConfig);
            final AsyncOutputStream out =
                    new AsyncOutputStream(streams.getLearnerOutput(), workGroup, in::isAlive, reconnectConfig);

            in.start();
            out.start();

            final int count = 100;

            for (int i = 0; i < count; i++) {
                out.sendAsync(viewId, new SerializableLong(i));
                final SerializableLong message = in.readAnticipatedMessage(viewId, SerializableLong::new);
                assertEquals(i, message.getValue(), "message should match the value that was serialized");
            }

            // Send reconnect completion marker
            streams.getLearnerOutput().writeInt(-1);
            streams.getLearnerOutput().flush();

            workGroup.waitForTermination();
        }
    }

    @Test
    @Tag(TestComponentTags.RECONNECT)
    @DisplayName("Max Output Queue Size")
    @Disabled
    void maxOutputQueueSize() throws InterruptedException, IOException {

        // Expected async output queue size
        final int bufferSize = 100 * reconnectConfig.maxParallelSubtrees();
        final int count = 1_000;

        final StandardWorkGroup workGroup = new StandardWorkGroup(getStaticThreadManager(), "test", null);

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final BlockingOutputStream blockingOut = new BlockingOutputStream(byteOut);

        // Block all bytes from this stream, data can only sit in async stream buffer
        blockingOut.lock();

        final int viewId = 13;
        final AsyncOutputStream out = new AsyncOutputStream(
                new SerializableDataOutputStream(blockingOut), workGroup, () -> true, reconnectConfig);

        out.start();

        final AtomicInteger messagesSent = new AtomicInteger(0);
        final Thread outputThread = new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    for (int i = 0; i < count; i++) {
                        try {
                            out.sendAsync(viewId, new SerializableLong(i));
                            messagesSent.getAndIncrement();
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            ex.printStackTrace();
                            break;
                        }
                    }
                })
                .setThreadName("output-thread")
                .build();
        outputThread.start();

        // Sender will send until the sender buffer is full.
        MILLISECONDS.sleep(100);

        final int messageCount = messagesSent.get();
        // The buffer will fill up, and one or more messages will be held by the sending thread (which is
        // blocked), up to double buffer size
        assertTrue(messageCount >= bufferSize + 1, "incorrect message count");
        assertTrue(messageCount <= 2 * bufferSize, "incorrect message count");

        // Unblock the buffer, allowing remaining messages to be sent
        blockingOut.unlock();
        MILLISECONDS.sleep(100);

        assertEquals(count, messagesSent.get(), "all messages should have been sent");

        workGroup.waitForTermination();

        // Sanity check, make sure all the messages were written to the stream
        final byte[] bytes = byteOut.toByteArray();
        final SerializableDataInputStream in = new SerializableDataInputStream(new ByteArrayInputStream(bytes));
        for (int i = 0; i < count; i++) {
            final int id = in.readInt();
            assertEquals(viewId, id, "View ID mismatch");
            final SerializableLong value = new SerializableLong();
            value.deserialize(in, value.getVersion());
            assertEquals(i, value.getValue(), "deserialized value should match expected value");
        }
    }

    @Test
    @Tag(TestComponentTags.RECONNECT)
    @DisplayName("Max Input Queue Size")
    @Disabled
    void maxInputQueueSize() throws IOException, InterruptedException {

        final int viewId = 14;

        final int bufferSize = 100;
        final int count = 1_000;
        final StandardWorkGroup workGroup = new StandardWorkGroup(getStaticThreadManager(), "test", null);

        // Write a bunch of stuff into the stream
        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);
        for (int i = 0; i < count; i++) {
            out.writeInt(viewId);
            // This is the way that each object is written by the AsyncOutputStream, mimic that format
            new SerializableLong(i).serialize(out);
        }
        MILLISECONDS.sleep(100);
        final byte[] data = byteOut.toByteArray();

        final BlockingInputStream blockingIn = new BlockingInputStream(new ByteArrayInputStream(data));

        final AsyncInputStream in =
                new AsyncInputStream(new SerializableDataInputStream(blockingIn), workGroup, reconnectConfig);
        in.start();

        // Give the stream some time to accept as much data as it wants. Stream will stop accepting when queue fills up.
        MILLISECONDS.sleep(100);

        // Lock the stream, preventing further reads
        blockingIn.lock();

        final AtomicInteger messagesReceived = new AtomicInteger(0);
        final Thread inputThread = new ThreadConfiguration(getStaticThreadManager())
                .setRunnable(() -> {
                    for (int i = 0; i < count; i++) {
                        try {
                            final SerializableLong message = in.readAnticipatedMessage(viewId, SerializableLong::new);
                            assertEquals(i, message.getValue(), "value does not match expected");
                            messagesReceived.getAndIncrement();
                        } catch (final IOException ex) {
                            throw new UncheckedIOException(ex);
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            ex.printStackTrace();
                            break;
                        }
                    }
                })
                .setThreadName("output-thread")
                .build();
        inputThread.start();

        // Let the input thread read from the buffer
        MILLISECONDS.sleep(100);

        // The number of messages should equal the buffer size
        // plus one message that was blocked from entering the buffer
        assertEquals(bufferSize + 1, messagesReceived.get(), "incorrect number of messages received");

        // Unblock the stream, remainder of messages should be read
        blockingIn.unlock();
        MILLISECONDS.sleep(100);

        assertEquals(count, messagesReceived.get(), "all messages should be read");

        // Send reconnect completion marker
        out.writeInt(-1);
        out.flush();

        workGroup.waitForTermination();
    }

    /**
     * This test verifies that a bug that once existed in AsyncInputStream has been fixed. This bug could case the
     * stream to deadlock during an abort.
     */
    @Test()
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("AsyncInputStream Deadlock")
    void asyncInputStreamAbortDeadlock() throws InterruptedException {
        try (final PairedStreams pairedStreams = new PairedStreams()) {

            final StandardWorkGroup workGroup =
                    new StandardWorkGroup(getStaticThreadManager(), "input-stream-abort-deadlock", null);

            final AsyncOutputStream teacherOut =
                    new AsyncOutputStream(pairedStreams.getTeacherOutput(), workGroup, () -> true, reconnectConfig);

            final int viewId = 15;
            final AsyncInputStream learnerIn =
                    new AsyncInputStream(pairedStreams.getLearnerInput(), workGroup, reconnectConfig);

            final Runnable reader = () -> {
                try {
                    learnerIn.readAnticipatedMessage(viewId, ExplodingSelfSerializable::new);
                } catch (final Exception e) {
                    workGroup.handleError(e);
                }
            };
            workGroup.execute(reader);

            learnerIn.start();
            teacherOut.start();

            teacherOut.sendAsync(viewId, new ExplodingSelfSerializable());
            Thread.sleep(100);

            workGroup.waitForTermination();
            assertTrue(workGroup.hasExceptions(), "work group is expected to have an exception");

            final Thread abortThread = new Thread(learnerIn::abort);

            abortThread.start();
            abortThread.join(1_000);
            if (abortThread.isAlive()) {
                abortThread.interrupt();
                fail("abort should have finished");
            }

            abortThread.join();

        } catch (final IOException e) {
            e.printStackTrace();
            fail("exception encountered", e);
        }
    }
}
