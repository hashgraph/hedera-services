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
import static com.swirlds.platform.recovery.RecoveryTestUtils.getFirstEventStreamFile;
import static com.swirlds.platform.recovery.RecoveryTestUtils.truncateFile;
import static com.swirlds.platform.recovery.RecoveryTestUtils.writeRandomEventStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.recovery.internal.EventStreamSingleFileIterator;
import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EventStreamSingleFileIterator Test")
class EventStreamSingleFileIteratorTest {

    public static void assertEventsAreEqual(final EventImpl expected, final EventImpl actual) {
        assertEquals(expected.getBaseEvent(), actual.getBaseEvent());
        assertEquals(expected.getConsensusData(), actual.getConsensusData());
    }

    @Test
    @DisplayName("Simple Stream Test")
    void simpleStreamTest() throws IOException, NoSuchAlgorithmException, ConstructableRegistryException {

        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final List<EventImpl> events = generateRandomEvents(random, 0L, Duration.ofSeconds(4), 1, 20);

        writeRandomEventStream(random, directory, 2, events);
        final Path eventStreamFile = getFirstEventStreamFile(directory);

        try (final EventStreamSingleFileIterator iterator = new EventStreamSingleFileIterator(eventStreamFile, false)) {
            assertNotNull(iterator.getStartHash(), "starting hash should be known");
            assertNull(iterator.getEndHash(), "end hash should not yet be known");

            int eventIndex = 0;

            while (iterator.hasNext()) {
                final DetailedConsensusEvent peekObject = iterator.peek();
                final DetailedConsensusEvent event = iterator.next();
                assertSame(event, peekObject, "invalid peek behavior");

                // Convert to event impl to allow comparison
                final EventImpl e = new EventImpl(
                        event.getBaseEventHashedData(), event.getBaseEventUnhashedData(), event.getConsensusData());
                assertEventsAreEqual(e, events.get(eventIndex));
                eventIndex++;
            }

            assertNotNull(iterator.getEndHash(), "end hash should now be known");

            assertThrows(NoSuchElementException.class, iterator::next, "no objects should remain");
            assertThrows(NoSuchElementException.class, iterator::peek, "no objects should remain");

        } finally {
            FileUtils.deleteDirectory(directory);
        }
    }

    @Disabled("this test is flaky")
    @Test
    @DisplayName("Allowed Truncated File Test")
    void allowedTruncatedFileTest() throws IOException, NoSuchAlgorithmException, ConstructableRegistryException {

        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final List<EventImpl> events = generateRandomEvents(random, 0L, Duration.ofSeconds(4), 1, 20);

        writeRandomEventStream(random, directory, 2, events);
        final Path eventStreamFile = getFirstEventStreamFile(directory);

        truncateFile(eventStreamFile, false);

        try (final EventStreamSingleFileIterator iterator = new EventStreamSingleFileIterator(eventStreamFile, true)) {
            assertNotNull(iterator.getStartHash(), "starting hash should be known");
            assertNull(iterator.getEndHash(), "end hash should not yet be known");

            int eventIndex = 0;

            while (iterator.hasNext()) {
                final DetailedConsensusEvent peekObject = iterator.peek();
                final DetailedConsensusEvent event = iterator.next();
                assertSame(event, peekObject, "invalid peek behavior");

                // Convert to event impl to allow comparison
                final EventImpl e = new EventImpl(
                        event.getBaseEventHashedData(), event.getBaseEventUnhashedData(), event.getConsensusData());
                assertEquals(e, events.get(eventIndex), "event should match input event");
                eventIndex++;
            }

            assertNull(iterator.getEndHash(), "file was truncated, hash should not be found");

            assertThrows(NoSuchElementException.class, iterator::next, "no objects should remain");
            assertThrows(NoSuchElementException.class, iterator::peek, "no objects should remain");

        } finally {
            FileUtils.deleteDirectory(directory);
        }
    }

    @Test
    @DisplayName("Disallowed Truncated File Test")
    void disallowedTruncatedFileTest() throws IOException, NoSuchAlgorithmException, ConstructableRegistryException {

        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final List<EventImpl> events = generateRandomEvents(random, 0L, Duration.ofSeconds(4), 1, 20);

        writeRandomEventStream(random, directory, 2, events);
        final Path eventStreamFile = getFirstEventStreamFile(directory);

        final int validObjectCount = truncateFile(eventStreamFile, false);

        boolean exceptionThrown = false;
        int count = 0;
        try (final EventStreamSingleFileIterator iterator = new EventStreamSingleFileIterator(eventStreamFile, false)) {
            assertNotNull(iterator.getStartHash(), "starting hash should be known");
            assertNull(iterator.getEndHash(), "end hash should not yet be known");

            int eventIndex = 0;

            while (iterator.hasNext()) {
                count++;
                final DetailedConsensusEvent peekObject = iterator.peek();
                final DetailedConsensusEvent event = iterator.next();
                assertSame(event, peekObject, "invalid peek behavior");

                // Convert to event impl to allow comparison
                final EventImpl e = new EventImpl(
                        event.getBaseEventHashedData(), event.getBaseEventUnhashedData(), event.getConsensusData());
                assertEventsAreEqual(e, events.get(eventIndex));
                eventIndex++;
            }

            assertNull(iterator.getEndHash(), "file was truncated, hash should not be found");

            assertThrows(NoSuchElementException.class, iterator::next, "no objects should remain");
            assertThrows(NoSuchElementException.class, iterator::peek, "no objects should remain");

        } catch (final IOException ioException) {
            assertEquals(validObjectCount - 1, count, "unexpected number of events read");
            exceptionThrown = true;
        } finally {
            FileUtils.deleteDirectory(directory);
        }

        assertTrue(exceptionThrown, "expecting read of truncated file to fail");
    }

    @Test
    @DisplayName("Disallowed Truncated File Test")
    void disallowedTruncatedOnBoundaryTest()
            throws IOException, NoSuchAlgorithmException, ConstructableRegistryException {

        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final List<EventImpl> events = generateRandomEvents(random, 0L, Duration.ofSeconds(4), 1, 20);

        writeRandomEventStream(random, directory, 2, events);
        final Path eventStreamFile = getFirstEventStreamFile(directory);

        final int validObjectCount = truncateFile(eventStreamFile, true);

        boolean exceptionThrown = false;
        int count = 0;
        try (final EventStreamSingleFileIterator iterator = new EventStreamSingleFileIterator(eventStreamFile, false)) {
            assertNotNull(iterator.getStartHash(), "starting hash should be known");
            assertNull(iterator.getEndHash(), "end hash should not yet be known");

            int eventIndex = 0;

            while (iterator.hasNext()) {
                count++;
                final DetailedConsensusEvent peekObject = iterator.peek();
                final DetailedConsensusEvent event = iterator.next();
                assertSame(event, peekObject, "invalid peek behavior");

                // Convert to event impl to allow comparison
                final EventImpl e = new EventImpl(
                        event.getBaseEventHashedData(), event.getBaseEventUnhashedData(), event.getConsensusData());
                assertEventsAreEqual(e, events.get(eventIndex));
                eventIndex++;
            }

            assertNull(iterator.getEndHash(), "file was truncated, hash should not be found");

            assertThrows(NoSuchElementException.class, iterator::next, "no objects should remain");
            assertThrows(NoSuchElementException.class, iterator::peek, "no objects should remain");

        } catch (final IOException ioException) {
            assertEquals(validObjectCount - 1, count, "unexpected number of events read");
            exceptionThrown = true;
        } finally {
            FileUtils.deleteDirectory(directory);
        }

        assertTrue(exceptionThrown, "expecting read of truncated file to fail");
    }
}
