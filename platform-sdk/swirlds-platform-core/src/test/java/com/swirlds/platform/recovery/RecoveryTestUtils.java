/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.recovery;

import static com.swirlds.base.units.UnitConstants.SECONDS_TO_NANOSECONDS;
import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.test.fixtures.RandomUtils.randomSignature;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.utility.CompareTo.isLessThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.extendable.ExtendableInputStream;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.event.stream.EventStreamManager;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.recovery.internal.ObjectStreamIterator;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.ConsensusData;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.Mockito;

/**
 * Utilities for recovery tests.
 */
public final class RecoveryTestUtils {

    private RecoveryTestUtils() {}

    /**
     * Generate a random event. Fields inside event are filled mostly with random nonsense data,
     * and a little realistic data (i.e. the event's round).
     *
     * @param random
     * 		a source of randomness
     * @return an event
     */
    public static EventImpl generateRandomEvent(
            final Random random, final long round, final boolean lastInRound, final Instant now) {

        final ConsensusTransactionImpl[] transactions = new ConsensusTransactionImpl[random.nextInt(10)];
        for (int transactionIndex = 0; transactionIndex < transactions.length; transactionIndex++) {
            final byte[] contents = new byte[random.nextInt(10) + 1];
            random.nextBytes(contents);
            transactions[transactionIndex] = new SwirldTransaction(contents);
        }

        final NodeId selfId = new NodeId(random.nextLong(Long.MAX_VALUE));
        final NodeId otherId = new NodeId(random.nextLong(Long.MAX_VALUE));

        final EventDescriptor selfDescriptor = new EventDescriptor(
                randomHash(random), selfId, random.nextLong(), EventConstants.BIRTH_ROUND_UNDEFINED);
        final EventDescriptor otherDescriptor = new EventDescriptor(
                randomHash(random), otherId, random.nextLong(), EventConstants.BIRTH_ROUND_UNDEFINED);

        final BaseEventHashedData baseEventHashedData = new BaseEventHashedData(
                new BasicSoftwareVersion(1),
                selfId,
                selfDescriptor,
                Collections.singletonList(otherDescriptor),
                EventConstants.BIRTH_ROUND_UNDEFINED,
                now,
                transactions);

        final BaseEventUnhashedData baseEventUnhashedData =
                new BaseEventUnhashedData(otherId, randomSignature(random).getSignatureBytes());

        final ConsensusData consensusData = new ConsensusData();
        consensusData.setConsensusTimestamp(now);
        consensusData.setRoundReceived(round);
        consensusData.setConsensusOrder(random.nextLong());
        consensusData.setLastInRoundReceived(lastInRound);

        final EventImpl event = new EventImpl(baseEventHashedData, baseEventUnhashedData, consensusData);
        event.setRoundCreated(random.nextLong());
        event.setStale(random.nextBoolean());
        return event;
    }

    /**
     * Generate a list of random events.
     *
     * @param random
     * 		a source of randomness
     * @param firstRound
     * 		the round of the first event
     * @param timeToSimulate
     * 		the length of time that should be simulated
     * @param roundsPerSecond
     * 		the number of rounds per second
     * @param evensPerRound
     * 		the number of events in each round
     * @return a list of events
     */
    public static List<EventImpl> generateRandomEvents(
            final Random random,
            final long firstRound,
            final Duration timeToSimulate,
            final int roundsPerSecond,
            final int evensPerRound) {

        final List<EventImpl> events = new ArrayList<>();

        final FakeTime time = new FakeTime();
        final Instant stopTime = time.now().plus(timeToSimulate);
        final Duration timeBetweenEvents =
                Duration.ofNanos((long) (1.0 * SECONDS_TO_NANOSECONDS / roundsPerSecond / evensPerRound));

        long round = firstRound;
        long eventsInRound = 0;

        while (isLessThan(time.now(), stopTime)) {

            final long currentRound = round;
            final Instant now = time.now();
            final boolean lastInRound;

            time.tick(timeBetweenEvents);
            eventsInRound++;
            if (eventsInRound >= evensPerRound) {
                round++;
                eventsInRound = 0;
                lastInRound = true;
            } else {
                lastInRound = false;
            }

            events.add(generateRandomEvent(random, currentRound, lastInRound, now));
        }

        return events;
    }

    /**
     * Write a list of events to event stream files.
     *
     * @param random
     * 		a source of randomness (for generating signatures)
     * @param destination
     * 		the directory where the files should be written
     * @param secondsPerFile
     * 		the number of seconds of data in each file
     * @param events
     * 		a list of events to be written
     */
    public static void writeRandomEventStream(
            final Random random, final Path destination, final int secondsPerFile, final List<EventImpl> events)
            throws NoSuchAlgorithmException, IOException {

        final EventStreamManager eventEventStreamManager = new EventStreamManager(
                TestPlatformContextBuilder.create().build(),
                Time.getCurrent(),
                getStaticThreadManager(),
                new NodeId(0L),
                x -> randomSignature(random),
                "test",
                true,
                destination.toString(),
                secondsPerFile,
                Integer.MAX_VALUE,
                x -> false);

        // The event stream writer has flaky asynchronous behavior,
        // so we need to be extra careful when waiting for it to finish.
        // Wrap events and count the number of times they are serialized.
        final AtomicInteger writeCount = new AtomicInteger(0);

        final List<EventImpl> wrappedEvents = new ArrayList<>(events.size());
        for (final EventImpl event : events) {
            final EventImpl wrappedEvent = spy(event);

            Mockito.doAnswer(invocation -> {
                        invocation.callRealMethod();
                        writeCount.incrementAndGet();
                        return null;
                    })
                    .when(wrappedEvent)
                    .serialize(any());

            wrappedEvents.add(wrappedEvent);
        }

        eventEventStreamManager.addEvents(wrappedEvents);

        // Each event will be serialized twice. Once when it is hashed, and once when it is written to disk.
        assertEventuallyTrue(
                () -> writeCount.get() == events.size() * 2,
                Duration.ofSeconds(10),
                "event not serialized fast enough");

        eventEventStreamManager.stop();
    }

    /**
     * Compare two event stream files based on creation date.
     */
    private static int compareEventStreamPaths(final Path pathA, final Path pathB) {
        // A nice property of dates is that they are naturally alphabetized by timestamp
        return pathA.getFileName().compareTo(pathB.getFileName());
    }

    /**
     * Check if a file is an event stream file.
     */
    private static boolean isFileAnEventStreamFile(final Path path) {
        return path.toString().endsWith(".evts");
    }

    /**
     * Get the first event stream file from a directory.
     */
    public static Path getFirstEventStreamFile(final Path directory) throws IOException {
        final List<Path> eventStreamFiles = new ArrayList<>();
        Files.walk(directory)
                .filter(RecoveryTestUtils::isFileAnEventStreamFile)
                .sorted(RecoveryTestUtils::compareEventStreamPaths)
                .forEachOrdered(eventStreamFiles::add);

        return eventStreamFiles.get(0);
    }

    /**
     * Get the middle event stream file from a directory.
     */
    public static Path getMiddleEventStreamFile(final Path directory) throws IOException {
        final List<Path> eventStreamFiles = new ArrayList<>();
        Files.walk(directory)
                .filter(RecoveryTestUtils::isFileAnEventStreamFile)
                .sorted(RecoveryTestUtils::compareEventStreamPaths)
                .forEachOrdered(eventStreamFiles::add);

        return eventStreamFiles.get(eventStreamFiles.size() / 2);
    }

    /**
     * Get the last event stream file from a directory.
     */
    public static Path getLastEventStreamFile(final Path directory) throws IOException {
        final List<Path> eventStreamFiles = new ArrayList<>();
        Files.walk(directory)
                .filter(RecoveryTestUtils::isFileAnEventStreamFile)
                .sorted(RecoveryTestUtils::compareEventStreamPaths)
                .forEachOrdered(eventStreamFiles::add);

        return eventStreamFiles.get(eventStreamFiles.size() - 1);
    }

    /**
     * Remove the second half of a file. Updates the file on disk.
     *
     * @param file
     * 		the file to truncate
     * @param truncateOnObjectBoundary
     * 		if true then truncate the file on an exact object boundary,
     * 		if false then truncate the file somewhere that isn't an object boundary
     * @return the number of valid objects in the truncated file
     */
    public static int truncateFile(final Path file, boolean truncateOnObjectBoundary) throws IOException {

        // Grab the raw bytes.
        final InputStream in = new BufferedInputStream(new FileInputStream(file.toFile()));
        final byte[] bytes = in.readAllBytes();
        in.close();

        // Read objects from the stream, and count the bytes at each object boundary.
        final Map<Integer, Integer> byteBoundaries = new HashMap<>();
        final CountingStreamExtension counter = new CountingStreamExtension();
        final ExtendableInputStream countingIn = new ExtendableInputStream(new FileInputStream(file.toFile()), counter);
        final IOIterator<SelfSerializable> iterator = new ObjectStreamIterator<>(countingIn, false);
        int count = 0;
        while (iterator.hasNext()) {
            byteBoundaries.put(count, (int) counter.getCount());
            iterator.next();
            count++;
        }
        iterator.close();

        Files.delete(file);

        final int objectIndex = count / 2;
        final int truncationIndex =
                truncateOnObjectBoundary ? byteBoundaries.get(objectIndex) : byteBoundaries.get(objectIndex) - 1;

        final byte[] truncatedBytes = new byte[truncationIndex];
        for (int i = 0; i < truncatedBytes.length; i++) {
            truncatedBytes[i] = bytes[i];
        }

        final OutputStream out = new BufferedOutputStream(new FileOutputStream(file.toFile()));
        out.write(truncatedBytes);
        out.close();

        return objectIndex + (truncateOnObjectBoundary ? 1 : 0);
    }
}
