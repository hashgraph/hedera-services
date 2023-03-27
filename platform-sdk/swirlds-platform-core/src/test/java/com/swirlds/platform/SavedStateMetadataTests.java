/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.state.signed.SavedStateMetadataField.WALL_CLOCK_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SavedStateMetadata;
import com.swirlds.platform.state.signed.SavedStateMetadataField;
import com.swirlds.platform.state.signed.SigSet;
import com.swirlds.platform.state.signed.SignedState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SignedStateMetadata Tests")
class SavedStateMetadataTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    /**
     * Serialize a metadata file then deserialize it and return the result.
     */
    private SavedStateMetadata serializeDeserialize(final SavedStateMetadata metadata) throws IOException {
        final Path path = testDirectory.resolve("metadata.txt");
        metadata.write(path);
        final SavedStateMetadata deserialized = SavedStateMetadata.parse(path);
        Files.delete(path);
        return deserialized;
    }

    @Test
    @DisplayName("Random Data Test")
    void randomDataTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final long round = random.nextLong();
        final long numberOfConsensusEvents = random.nextLong();
        final Instant timestamp = RandomUtils.randomInstant(random);
        final Hash runningEventHash = RandomUtils.randomHash(random);
        final long minimumGenerationNonAncient = random.nextLong();
        final SoftwareVersion softwareVersion = new BasicSoftwareVersion(random.nextLong());
        final Instant wallClockTime = RandomUtils.randomInstant(random);
        final long nodeId = random.nextLong();
        final List<Long> signingNodes = new ArrayList<>();
        for (int i = 0; i < random.nextInt(1, 10); i++) {
            signingNodes.add(random.nextLong());
        }
        final long signingStakeSum = random.nextLong();
        final long totalStake = random.nextLong();

        final SavedStateMetadata metadata = new SavedStateMetadata(
                round,
                numberOfConsensusEvents,
                timestamp,
                runningEventHash,
                minimumGenerationNonAncient,
                softwareVersion.toString(),
                wallClockTime,
                nodeId,
                signingNodes,
                signingStakeSum,
                totalStake);

        final SavedStateMetadata deserialized = serializeDeserialize(metadata);

        assertEquals(round, deserialized.round());
        assertEquals(numberOfConsensusEvents, deserialized.numberOfConsensusEvents());
        assertEquals(timestamp, deserialized.consensusTimestamp());
        assertEquals(runningEventHash, deserialized.runningEventHash());
        assertEquals(minimumGenerationNonAncient, deserialized.minimumGenerationNonAncient());
        assertEquals(softwareVersion.toString(), deserialized.softwareVersion());
        assertEquals(wallClockTime, deserialized.wallClockTime());
        assertEquals(nodeId, deserialized.nodeId());
        assertEquals(signingNodes, deserialized.signingNodes());
        assertEquals(signingStakeSum, deserialized.signingStakeSum());
        assertEquals(totalStake, deserialized.totalStake());
    }

    @Test
    @DisplayName("Random Data Some Missing Test")
    void randomDataSomeMissingTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final Long round;
        if (random.nextBoolean()) {
            round = random.nextLong();
        } else {
            round = null;
        }

        final Long numberOfConsensusEvents;
        if (random.nextBoolean()) {
            numberOfConsensusEvents = random.nextLong();
        } else {
            numberOfConsensusEvents = null;
        }

        final Instant timestamp;
        if (random.nextBoolean()) {
            timestamp = RandomUtils.randomInstant(random);
        } else {
            timestamp = null;
        }

        final Hash runningEventHash;
        if (random.nextBoolean()) {
            runningEventHash = RandomUtils.randomHash(random);
        } else {
            runningEventHash = null;
        }

        final Long minimumGenerationNonAncient;
        if (random.nextBoolean()) {
            minimumGenerationNonAncient = random.nextLong();
        } else {
            minimumGenerationNonAncient = null;
        }

        final SoftwareVersion softwareVersion;
        if (random.nextBoolean()) {
            softwareVersion = new BasicSoftwareVersion(random.nextLong());
        } else {
            softwareVersion = null;
        }

        final Instant wallClockTime;
        if (random.nextBoolean()) {
            wallClockTime = RandomUtils.randomInstant(random);
        } else {
            wallClockTime = null;
        }

        final Long nodeId;
        if (random.nextBoolean()) {
            nodeId = random.nextLong();
        } else {
            nodeId = null;
        }

        final List<Long> signingNodes;
        if (random.nextBoolean()) {
            signingNodes = new ArrayList<>();
            for (int i = 0; i < random.nextInt(1, 100); i++) {
                signingNodes.add(random.nextLong());
            }
        } else {
            signingNodes = null;
        }

        final Long signingStakeSum;
        if (random.nextBoolean()) {
            signingStakeSum = random.nextLong();
        } else {
            signingStakeSum = null;
        }

        final Long totalStake;
        if (random.nextBoolean()) {
            totalStake = random.nextLong();
        } else {
            totalStake = null;
        }

        final SavedStateMetadata metadata = new SavedStateMetadata(
                round,
                numberOfConsensusEvents,
                timestamp,
                runningEventHash,
                minimumGenerationNonAncient,
                softwareVersion == null ? null : softwareVersion.toString(),
                wallClockTime,
                nodeId,
                signingNodes,
                signingStakeSum,
                totalStake);

        final SavedStateMetadata deserialized = serializeDeserialize(metadata);

        assertEquals(round, deserialized.round());
        assertEquals(numberOfConsensusEvents, deserialized.numberOfConsensusEvents());
        assertEquals(timestamp, deserialized.consensusTimestamp());
        assertEquals(runningEventHash, deserialized.runningEventHash());
        assertEquals(minimumGenerationNonAncient, deserialized.minimumGenerationNonAncient());
        if (softwareVersion == null) {
            assertNull(deserialized.softwareVersion());
        } else {
            assertEquals(softwareVersion.toString(), deserialized.softwareVersion());
        }
        assertEquals(wallClockTime, deserialized.wallClockTime());
        assertEquals(nodeId, deserialized.nodeId());
        assertEquals(signingNodes, deserialized.signingNodes());
        assertEquals(signingStakeSum, deserialized.signingStakeSum());
        assertEquals(totalStake, deserialized.totalStake());
    }

    @Test
    @DisplayName("Signing Nodes Sorted Test")
    void signingNodesSortedTest() {
        final SignedState signedState = mock(SignedState.class);
        final SigSet sigSet = mock(SigSet.class);
        final State state = mock(State.class);
        final PlatformState platformState = mock(PlatformState.class);
        final PlatformData platformData = mock(PlatformData.class);
        final AddressBook addressBook = mock(AddressBook.class);

        when(signedState.getState()).thenReturn(state);
        when(state.getPlatformState()).thenReturn(platformState);
        when(platformState.getAddressBook()).thenReturn(addressBook);
        when(platformState.getPlatformData()).thenReturn(platformData);
        when(signedState.getSigSet()).thenReturn(sigSet);
        when(sigSet.getSigningNodes()).thenReturn(new ArrayList<>(List.of(3L, 1L, 2L)));

        final SavedStateMetadata metadata = SavedStateMetadata.create(signedState, 1234, Instant.now());

        assertEquals(List.of(1L, 2L, 3L), metadata.signingNodes());
    }

    @Test
    @DisplayName("Handle Newlines Elegantly Test")
    void handleNewlinesElegantlyTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final long round = random.nextLong();
        final long numberOfConsensusEvents = random.nextLong();
        final Instant timestamp = RandomUtils.randomInstant(random);
        final Hash runningEventHash = RandomUtils.randomHash(random);
        final long minimumGenerationNonAncient = random.nextLong();
        final Instant wallClockTime = RandomUtils.randomInstant(random);
        final long nodeId = random.nextLong();
        final List<Long> signingNodes = new ArrayList<>();
        for (int i = 0; i < random.nextInt(1, 10); i++) {
            signingNodes.add(random.nextLong());
        }
        final long signingStakeSum = random.nextLong();
        final long totalStake = random.nextLong();

        final SavedStateMetadata metadata = new SavedStateMetadata(
                round,
                numberOfConsensusEvents,
                timestamp,
                runningEventHash,
                minimumGenerationNonAncient,
                "why\nare\nthere\nnewlines\nhere\nplease\nstop\n",
                wallClockTime,
                nodeId,
                signingNodes,
                signingStakeSum,
                totalStake);

        final SavedStateMetadata deserialized = serializeDeserialize(metadata);

        assertEquals(round, deserialized.round());
        assertEquals(numberOfConsensusEvents, deserialized.numberOfConsensusEvents());
        assertEquals(timestamp, deserialized.consensusTimestamp());
        assertEquals(runningEventHash, deserialized.runningEventHash());
        assertEquals(minimumGenerationNonAncient, deserialized.minimumGenerationNonAncient());
        assertEquals("why//are//there//newlines//here//please//stop//", deserialized.softwareVersion());
        assertEquals(wallClockTime, deserialized.wallClockTime());
        assertEquals(nodeId, deserialized.nodeId());
        assertEquals(signingNodes, deserialized.signingNodes());
        assertEquals(signingStakeSum, deserialized.signingStakeSum());
        assertEquals(totalStake, deserialized.totalStake());
    }

    private interface FileUpdater {
        String createNewFileString(final String fileString, SavedStateMetadata metadata) throws IOException;
    }

    /**
     * Test the parsing of a mal-formatted file
     * @param random a source of randomness
     * @param fileUpdater updates a file in some way
     * @param invalidFields the fields expected to be invalid in the mal-formatted file
     */
    private void testMalFormedFile(
            final Random random, final FileUpdater fileUpdater, final Set<SavedStateMetadataField> invalidFields)
            throws IOException {

        final long round = random.nextLong();
        final long numberOfConsensusEvents = random.nextLong();
        final Instant timestamp = RandomUtils.randomInstant(random);
        final Hash runningEventHash = RandomUtils.randomHash(random);
        final long minimumGenerationNonAncient = random.nextLong();
        final SoftwareVersion softwareVersion = new BasicSoftwareVersion(random.nextLong());
        final Instant wallClockTime = RandomUtils.randomInstant(random);
        final long nodeId = random.nextLong();
        final List<Long> signingNodes = new ArrayList<>();
        for (int i = 0; i < random.nextInt(1, 10); i++) {
            signingNodes.add(random.nextLong());
        }
        final long signingStakeSum = random.nextLong();
        final long totalStake = random.nextLong();

        final SavedStateMetadata metadata = new SavedStateMetadata(
                round,
                numberOfConsensusEvents,
                timestamp,
                runningEventHash,
                minimumGenerationNonAncient,
                softwareVersion.toString(),
                wallClockTime,
                nodeId,
                signingNodes,
                signingStakeSum,
                totalStake);

        final Path path = testDirectory.resolve("metadata.txt");
        metadata.write(path);

        final String fileString = new String(Files.readAllBytes(path));
        final String brokenString = fileUpdater.createNewFileString(fileString, metadata);
        Files.delete(path);
        if (brokenString != null) {
            Files.write(path, brokenString.getBytes());
        }

        final SavedStateMetadata deserialized = SavedStateMetadata.parse(path);

        if (brokenString != null) {
            Files.delete(path);
        }

        if (invalidFields.contains(SavedStateMetadataField.ROUND)) {
            assertNull(deserialized.round());
        } else {
            assertEquals(round, deserialized.round());
        }

        if (invalidFields.contains(SavedStateMetadataField.NUMBER_OF_CONSENSUS_EVENTS)) {
            assertNull(deserialized.numberOfConsensusEvents());
        } else {
            assertEquals(numberOfConsensusEvents, deserialized.numberOfConsensusEvents());
        }

        if (invalidFields.contains(SavedStateMetadataField.CONSENSUS_TIMESTAMP)) {
            assertNull(deserialized.consensusTimestamp());
        } else {
            assertEquals(timestamp, deserialized.consensusTimestamp());
        }

        if (invalidFields.contains(SavedStateMetadataField.RUNNING_EVENT_HASH)) {
            assertNull(deserialized.runningEventHash());
        } else {
            assertEquals(runningEventHash, deserialized.runningEventHash());
        }

        if (invalidFields.contains(SavedStateMetadataField.MINIMUM_GENERATION_NON_ANCIENT)) {
            assertNull(deserialized.minimumGenerationNonAncient());
        } else {
            assertEquals(minimumGenerationNonAncient, deserialized.minimumGenerationNonAncient());
        }

        if (invalidFields.contains(SavedStateMetadataField.SOFTWARE_VERSION)) {
            assertNull(deserialized.softwareVersion());
        } else {
            assertEquals(softwareVersion.toString(), deserialized.softwareVersion());
        }

        if (invalidFields.contains(SavedStateMetadataField.WALL_CLOCK_TIME)) {
            assertNull(deserialized.wallClockTime());
        } else {
            assertEquals(wallClockTime, deserialized.wallClockTime());
        }

        if (invalidFields.contains(SavedStateMetadataField.NODE_ID)) {
            assertNull(deserialized.nodeId());
        } else {
            assertEquals(nodeId, deserialized.nodeId());
        }

        if (invalidFields.contains(SavedStateMetadataField.SIGNING_NODES)) {
            assertNull(deserialized.signingNodes());
        } else {
            assertEquals(signingNodes, deserialized.signingNodes());
        }

        if (invalidFields.contains(SavedStateMetadataField.SIGNING_STAKE_SUM)) {
            assertNull(deserialized.signingStakeSum());
        } else {
            assertEquals(signingStakeSum, deserialized.signingStakeSum());
        }

        if (invalidFields.contains(SavedStateMetadataField.TOTAL_STAKE)) {
            assertNull(deserialized.totalStake());
        } else {
            assertEquals(totalStake, deserialized.totalStake());
        }
    }

    @Test
    @DisplayName("Empty File Test")
    void emptyFileTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final Set<SavedStateMetadataField> allFields =
                Arrays.stream(SavedStateMetadataField.values()).collect(Collectors.toSet());

        testMalFormedFile(random, (s, m) -> "", allFields);
    }

    @Test
    @DisplayName("Non-Existent File Test")
    void nonExistentFileFileTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final Set<SavedStateMetadataField> allFields =
                Arrays.stream(SavedStateMetadataField.values()).collect(Collectors.toSet());

        testMalFormedFile(random, (s, m) -> null, allFields);
    }

    @Test
    @DisplayName("Invalid Field Test")
    void invalidFieldTest() throws IOException {
        final Random random = getRandomPrintSeed();
        testMalFormedFile(
                random,
                (s, m) -> s.replace(SavedStateMetadataField.ROUND.toString(), "NOT_A_REAL_FIELD"),
                Set.of(SavedStateMetadataField.ROUND));
    }

    @Test
    @DisplayName("Invalid Long Test")
    void invalidLongTest() throws IOException {
        final Random random = getRandomPrintSeed();
        testMalFormedFile(
                random,
                (s, m) -> s.replace(m.nodeId().toString(), "NOT_A_REAL_LONG"),
                Set.of(SavedStateMetadataField.NODE_ID));
    }

    @Test
    @DisplayName("Invalid Instant Test")
    void invalidInstantTest() throws IOException {
        final Random random = getRandomPrintSeed();
        testMalFormedFile(
                random,
                (s, m) -> s.replace(m.wallClockTime().toString(), "NOT_A_REAL_TIME"),
                Set.of(SavedStateMetadataField.WALL_CLOCK_TIME));
    }

    @Test
    @DisplayName("Invalid Long List Test")
    void invalidLongListTest() throws IOException {
        final Random random = getRandomPrintSeed();
        testMalFormedFile(
                random,
                (s, m) -> {
                    final StringBuilder sb = new StringBuilder();

                    for (final String line : s.split("\n")) {
                        if (line.contains(SavedStateMetadataField.SIGNING_NODES.toString())) {
                            sb.append(SavedStateMetadataField.SIGNING_NODES + ": 1,2,3,4,herpderp,6,7,8\n");
                        } else {
                            sb.append(line).append("\n");
                        }
                    }

                    return sb.toString();
                },
                Set.of(SavedStateMetadataField.SIGNING_NODES));

        testMalFormedFile(
                random,
                (s, m) -> {
                    final StringBuilder sb = new StringBuilder();

                    for (final String line : s.split("\n")) {
                        if (line.contains(SavedStateMetadataField.SIGNING_NODES.toString())) {
                            sb.append(SavedStateMetadataField.SIGNING_NODES + ": 1,2,3,4,,6,7,8\n");
                        } else {
                            sb.append(line).append("\n");
                        }
                    }

                    return sb.toString();
                },
                Set.of(SavedStateMetadataField.SIGNING_NODES));

        testMalFormedFile(
                random,
                (s, m) -> {
                    final StringBuilder sb = new StringBuilder();

                    for (final String line : s.split("\n")) {
                        if (line.contains(SavedStateMetadataField.SIGNING_NODES.toString())) {
                            sb.append(SavedStateMetadataField.SIGNING_NODES + ": \n");
                        } else {
                            sb.append(line).append("\n");
                        }
                    }

                    return sb.toString();
                },
                Set.of(SavedStateMetadataField.SIGNING_NODES));

        // Whitespace in list shouldn't hurt anything
        testMalFormedFile(random, (s, m) -> s.replace(",", "   ,   "), Set.of());
    }

    @Test
    @DisplayName("Line Missing Colon Test")
    void lineMissingColonTest() throws IOException {
        final Random random = getRandomPrintSeed();
        testMalFormedFile(
                random,
                (s, m) -> s.replace(
                        SavedStateMetadataField.WALL_CLOCK_TIME + ":",
                        SavedStateMetadataField.WALL_CLOCK_TIME.toString()),
                Set.of(SavedStateMetadataField.WALL_CLOCK_TIME));
    }

    @Test
    @DisplayName("Invalid Hash Test")
    void invalidHashTest() throws IOException {
        final Random random = getRandomPrintSeed();
        testMalFormedFile(
                random,
                (s, m) -> s.replace(m.runningEventHash().toString(), "NOT_A_REAL_HASH"),
                Set.of(SavedStateMetadataField.RUNNING_EVENT_HASH));
    }

    @Test
    @DisplayName("Extra Whitespace Test")
    void extraWhitespaceTest() throws IOException {
        final Random random = getRandomPrintSeed();
        testMalFormedFile(
                random,
                (s, m) -> {
                    final StringBuilder sb = new StringBuilder();

                    for (final String line : s.split("\n")) {
                        if (line.contains(SavedStateMetadataField.WALL_CLOCK_TIME.toString())) {
                            sb.append("   \t ")
                                    .append(WALL_CLOCK_TIME)
                                    .append("  \t   :  \t    ")
                                    .append(m.wallClockTime())
                                    .append("   \t\n");
                        } else {
                            sb.append(line).append("\n");
                        }
                    }

                    return sb.toString();
                },
                Set.of());
    }
}
