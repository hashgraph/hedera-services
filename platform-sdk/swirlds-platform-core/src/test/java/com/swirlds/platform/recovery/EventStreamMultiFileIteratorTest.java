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

package com.swirlds.platform.recovery;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.recovery.RecoveryTestUtils.generateRandomEvents;
import static com.swirlds.platform.recovery.RecoveryTestUtils.getLastEventStreamFile;
import static com.swirlds.platform.recovery.RecoveryTestUtils.getMiddleEventStreamFile;
import static com.swirlds.platform.recovery.RecoveryTestUtils.truncateFile;
import static com.swirlds.platform.recovery.RecoveryTestUtils.writeRandomEventStream;
import static com.swirlds.platform.recovery.internal.EventStreamPathIterator.FIRST_ROUND_AVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.recovery.internal.EventStreamMultiFileIterator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EventStreamMultiFileIterator")
class EventStreamMultiFileIteratorTest {

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
        SettingsCommon.maxTransactionBytesPerEvent = Integer.MAX_VALUE;
        SettingsCommon.maxTransactionCountPerEvent = Integer.MAX_VALUE;
        SettingsCommon.transactionMaxBytes = Integer.MAX_VALUE;
        SettingsCommon.maxAddressSizeAllowed = Integer.MAX_VALUE;
    }

    @Test
    @DisplayName("Read All Events Test")
    void readAllEventsTest() throws IOException, NoSuchAlgorithmException {
        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final int durationInSeconds = 100;
        final int secondsPerFile = 2;

        final List<EventImpl> events = generateRandomEvents(random, 1L, Duration.ofSeconds(durationInSeconds), 1, 20);

        writeRandomEventStream(random, directory, secondsPerFile, events);

        try (final IOIterator<DetailedConsensusEvent> iterator =
                new EventStreamMultiFileIterator(directory, FIRST_ROUND_AVAILABLE)) {

            final List<DetailedConsensusEvent> deserializedEvents = new ArrayList<>();
            iterator.forEachRemaining(deserializedEvents::add);

            assertEquals(events.size(), deserializedEvents.size(), "unexpected number of events read");

            for (int eventIndex = 0; eventIndex < events.size(); eventIndex++) {

                final DetailedConsensusEvent event = deserializedEvents.get(eventIndex);

                // Convert to event impl to allow comparison
                final EventImpl e = new EventImpl(
                        event.getBaseEventHashedData(), event.getBaseEventUnhashedData(), event.getConsensusData());
                assertEquals(e, events.get(eventIndex), "event should match input event");
            }

        } finally {
            FileUtils.deleteDirectory(directory);
        }
    }

    @Test
    @DisplayName("Read Events Starting At Round Test")
    void readEventsStartingAtRoundTest() throws NoSuchAlgorithmException, IOException {
        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final int durationInSeconds = 100;
        final int secondsPerFile = 2;

        final List<EventImpl> events = generateRandomEvents(random, 1L, Duration.ofSeconds(durationInSeconds), 1, 20);

        // Figure out which event is the first one we expect to see
        final int readStartingAtRound = 10;
        int startingIndex = 0;
        while (events.get(startingIndex).getRoundReceived() < readStartingAtRound) {
            startingIndex++;
        }

        writeRandomEventStream(random, directory, secondsPerFile, events);

        try (final IOIterator<DetailedConsensusEvent> iterator =
                new EventStreamMultiFileIterator(directory, readStartingAtRound)) {

            final List<DetailedConsensusEvent> deserializedEvents = new ArrayList<>();
            iterator.forEachRemaining(deserializedEvents::add);

            for (int eventIndex = 0; eventIndex < events.size() - startingIndex; eventIndex++) {

                final DetailedConsensusEvent event = deserializedEvents.get(eventIndex);

                // Convert to event impl to allow comparison
                final EventImpl e = new EventImpl(
                        event.getBaseEventHashedData(), event.getBaseEventUnhashedData(), event.getConsensusData());
                assertEquals(e, events.get(eventIndex + startingIndex), "event should match input event");
            }

        } finally {
            FileUtils.deleteDirectory(directory);
        }
    }

    @Test
    @DisplayName("Read Events Starting At Non-Existent Round Test")
    void readEventsStartingAtNonExistentRoundTest() throws NoSuchAlgorithmException, IOException {

        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final int durationInSeconds = 100;
        final int secondsPerFile = 2;
        final long firstRound = 50;

        final List<EventImpl> events =
                generateRandomEvents(random, firstRound, Duration.ofSeconds(durationInSeconds), 1, 20);

        // Figure out which event is the first one we expect to see
        final int readStartingAtRound = 10;
        int startingIndex = 0;
        while (events.get(startingIndex).getRoundReceived() < readStartingAtRound) {
            startingIndex++;
        }

        writeRandomEventStream(random, directory, secondsPerFile, events);

        assertThrows(
                NoSuchElementException.class,
                () -> new EventStreamMultiFileIterator(directory, readStartingAtRound),
                "should throw if events are not available");

        FileUtils.deleteDirectory(directory);
    }

    @Test
    @DisplayName("Read Events Starting At Non-Existent Round Test")
    void missingEventStreamFileTest() throws IOException, NoSuchAlgorithmException {

        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final int durationInSeconds = 100;
        final int secondsPerFile = 2;

        final List<EventImpl> events = generateRandomEvents(random, 1L, Duration.ofSeconds(durationInSeconds), 1, 20);

        writeRandomEventStream(random, directory, secondsPerFile, events);

        final Path fileToDelete = getMiddleEventStreamFile(directory);
        Files.delete(fileToDelete);

        boolean readFailed = false;
        try (final IOIterator<DetailedConsensusEvent> iterator =
                new EventStreamMultiFileIterator(directory, FIRST_ROUND_AVAILABLE)) {

            final List<DetailedConsensusEvent> deserializedEvents = new ArrayList<>();
            iterator.forEachRemaining(deserializedEvents::add);

            assertEquals(events.size(), deserializedEvents.size(), "unexpected number of events read");

            for (int eventIndex = 0; eventIndex < events.size(); eventIndex++) {

                final DetailedConsensusEvent event = deserializedEvents.get(eventIndex);

                // Convert to event impl to allow comparison
                final EventImpl e = new EventImpl(
                        event.getBaseEventHashedData(), event.getBaseEventUnhashedData(), event.getConsensusData());
                assertEquals(e, events.get(eventIndex), "event should match input event");
            }

        } catch (final IOException ioException) {
            readFailed = true;
        } finally {
            FileUtils.deleteDirectory(directory);
        }

        assertTrue(readFailed, "should have been unable to read with missing file");
    }

    @Test
    @DisplayName("Truncate Last File Test")
    @Disabled("Fails Randomly in Github PRs. https://github.com/hashgraph/hedera-services/issues/5450")
    void truncatedLastFileTest() throws NoSuchAlgorithmException, IOException {
        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final int durationInSeconds = 100;
        final int secondsPerFile = 2;

        final List<EventImpl> events = generateRandomEvents(random, 1L, Duration.ofSeconds(durationInSeconds), 1, 20);

        writeRandomEventStream(random, directory, secondsPerFile, events);

        final Path lastFile = getLastEventStreamFile(directory);
        truncateFile(lastFile, false);

        try (final IOIterator<DetailedConsensusEvent> iterator =
                new EventStreamMultiFileIterator(directory, FIRST_ROUND_AVAILABLE)) {

            final List<DetailedConsensusEvent> deserializedEvents = new ArrayList<>();
            iterator.forEachRemaining(deserializedEvents::add);

            assertTrue(deserializedEvents.size() > 0, "some events should have been deserialized");
            assertTrue(events.size() > deserializedEvents.size(), "some events should not have been deserialized");

            for (int eventIndex = 0; eventIndex < deserializedEvents.size(); eventIndex++) {

                final DetailedConsensusEvent event = deserializedEvents.get(eventIndex);

                // Convert to event impl to allow comparison
                final EventImpl e = new EventImpl(
                        event.getBaseEventHashedData(), event.getBaseEventUnhashedData(), event.getConsensusData());
                assertEquals(e, events.get(eventIndex), "event should match input event");
            }

        } finally {
            FileUtils.deleteDirectory(directory);
        }
    }

    @Test
    @DisplayName("Truncate Middle File Test")
    void truncatedMiddleFileTest() throws NoSuchAlgorithmException, IOException {
        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final int durationInSeconds = 100;
        final int secondsPerFile = 2;

        final List<EventImpl> events = generateRandomEvents(random, 1L, Duration.ofSeconds(durationInSeconds), 1, 20);

        writeRandomEventStream(random, directory, secondsPerFile, events);

        final Path fileToTruncate = getMiddleEventStreamFile(directory);
        truncateFile(fileToTruncate, false);

        boolean readFailed = false;
        try (final IOIterator<DetailedConsensusEvent> iterator =
                new EventStreamMultiFileIterator(directory, FIRST_ROUND_AVAILABLE)) {

            final List<DetailedConsensusEvent> deserializedEvents = new ArrayList<>();
            iterator.forEachRemaining(deserializedEvents::add);

            assertEquals(events.size(), deserializedEvents.size(), "unexpected number of events read");

            for (int eventIndex = 0; eventIndex < events.size(); eventIndex++) {

                final DetailedConsensusEvent event = deserializedEvents.get(eventIndex);

                // Convert to event impl to allow comparison
                final EventImpl e = new EventImpl(
                        event.getBaseEventHashedData(), event.getBaseEventUnhashedData(), event.getConsensusData());
                assertEquals(e, events.get(eventIndex), "event should match input event");
            }

        } catch (final IOException ioException) {
            readFailed = true;
        } finally {
            FileUtils.deleteDirectory(directory);
        }

        assertTrue(readFailed, "should have been unable to read with missing file");
    }
}
