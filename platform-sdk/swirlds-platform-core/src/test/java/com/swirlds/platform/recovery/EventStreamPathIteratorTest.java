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
import static com.swirlds.common.utility.CompareTo.isLessThan;
import static com.swirlds.platform.recovery.RecoveryTestUtils.generateRandomEvents;
import static com.swirlds.platform.recovery.RecoveryTestUtils.writeRandomEventStream;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.recovery.internal.EventStreamPathIterator;
import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EventStreamPathIterator Test")
class EventStreamPathIteratorTest {

    @Test
    @DisplayName("Starting From First Event Test")
    void startingFromFirstEventTest() throws IOException, NoSuchAlgorithmException, ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final int durationInSeconds = 100;
        final int secondsPerFile = 2;
        final int expectedFileCount = durationInSeconds / secondsPerFile;

        final List<EventImpl> events = generateRandomEvents(random, 1L, Duration.ofSeconds(durationInSeconds), 1, 20);

        writeRandomEventStream(random, directory, secondsPerFile, events);

        final Iterator<Path> iterator =
                new EventStreamPathIterator(directory, EventStreamPathIterator.FIRST_ROUND_AVAILABLE);

        final List<Path> files = new ArrayList<>();
        iterator.forEachRemaining(files::add);

        Path prev = null;
        for (final Path path : files) {
            assertTrue(path.toString().endsWith(".evts"), "invalid file extension");
            assertTrue(path.startsWith(directory.toString()), "file not in expected directory");

            if (prev != null) {
                assertTrue(
                        isLessThan(prev.getFileName(), path.getFileName()),
                        "files should be returned in alphabetical order");
            }
            prev = path;
        }

        assertTrue(
                files.size() >= expectedFileCount - 1 && files.size() <= expectedFileCount + 1,
                "unexpected number of files");

        FileUtils.deleteDirectory(directory);
    }

    @Test
    @DisplayName("Starting From Specified Event Test")
    void startingFromSpecifiedEventTest() throws IOException, NoSuchAlgorithmException, ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final int durationInSeconds = 100;
        final int roundsPerSecond = 1;
        final int secondsPerFile = 2;

        final long startingRound = durationInSeconds * roundsPerSecond / 2;
        final int expectedFileCount = durationInSeconds / secondsPerFile / 2;

        final List<EventImpl> events =
                generateRandomEvents(random, 1L, Duration.ofSeconds(durationInSeconds), roundsPerSecond, 20);

        writeRandomEventStream(random, directory, secondsPerFile, events);

        final Iterator<Path> iterator = new EventStreamPathIterator(directory, startingRound);

        final List<Path> files = new ArrayList<>();
        iterator.forEachRemaining(files::add);

        Path prev = null;
        for (final Path path : files) {
            assertTrue(path.toString().endsWith(".evts"), "invalid file extension");
            assertTrue(path.startsWith(directory.toString()), "file not in expected directory");

            if (prev != null) {
                assertTrue(
                        isLessThan(prev.getFileName(), path.getFileName()),
                        "files should be returned in alphabetical order");
            }
            prev = path;
        }

        // We are starting roughly in the middle, so make sure we have roughly half the total files
        assertTrue(
                files.size() >= expectedFileCount - 2 && files.size() <= expectedFileCount + 2,
                "unexpected number of files");

        FileUtils.deleteDirectory(directory);
    }

    @Test
    @DisplayName("Request Non Existent Rounds Test")
    void requestNonExistentRoundsTest() throws ConstructableRegistryException, IOException, NoSuchAlgorithmException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        final Random random = getRandomPrintSeed();
        final Path directory = TemporaryFileBuilder.buildTemporaryDirectory();

        final int durationInSeconds = 100;
        final int roundsPerSecond = 1;
        final int secondsPerFile = 2;

        final long firstRound = 100;
        final long requestedRound = 50;

        final List<EventImpl> events =
                generateRandomEvents(random, firstRound, Duration.ofSeconds(durationInSeconds), roundsPerSecond, 20);

        writeRandomEventStream(random, directory, secondsPerFile, events);

        assertThrows(
                NoSuchElementException.class,
                () -> new EventStreamPathIterator(directory, requestedRound),
                "should not be able to find the file for the given round");

        FileUtils.deleteDirectory(directory);
    }
}
