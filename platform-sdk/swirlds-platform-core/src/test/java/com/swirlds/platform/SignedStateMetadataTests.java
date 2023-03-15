package com.swirlds.platform;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.state.signed.SignedStateMetadata;
import com.swirlds.platform.state.signed.SignedStateMetadataField;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SignedStateMetadata Tests")
class SignedStateMetadataTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    /**
     * Serialize a metadata file then deserialize it and return the result.
     */
    private SignedStateMetadata serializeDeserialize(final SignedStateMetadata metadata) throws IOException {
        final Path path = testDirectory.resolve("metadata.txt");
        metadata.write(path);
        final SignedStateMetadata deserialized = SignedStateMetadata.parse(path);
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
        for (int i = 0; i < random.nextInt(10); i++) {
            signingNodes.add(random.nextLong());
        }
        final long signingStakeSum = random.nextLong();
        final long totalStake = random.nextLong();

        final SignedStateMetadata metadata = new SignedStateMetadata(
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

        final SignedStateMetadata deserialized = serializeDeserialize(metadata);

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
            for (int i = 0; i < random.nextInt(100); i++) {
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

        final SignedStateMetadata metadata = new SignedStateMetadata(
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

        final SignedStateMetadata deserialized = serializeDeserialize(metadata);

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
    @DisplayName("Mal-Formed File Test")
    void malFormedFileTest() throws IOException {
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
        for (int i = 0; i < random.nextInt(10); i++) {
            signingNodes.add(random.nextLong());
        }
        final long signingStakeSum = random.nextLong();
        final long totalStake = random.nextLong();

        final SignedStateMetadata metadata = new SignedStateMetadata(
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

        // Intentionally break this file
        String fileString = new String(Files.readAllBytes(path));

        // Change the name of a field
        fileString = fileString.replace(SignedStateMetadataField.ROUND.toString(), "NOT_A_REAL_FIELD");

        // Break the data formatting of a field
        fileString = fileString.replace(wallClockTime.toString(), "NOT_A_REAL_TIME");

        // write the file back
        Files.write(path, fileString.getBytes());

        final SignedStateMetadata deserialized = SignedStateMetadata.parse(path);
        Files.delete(path);

        assertNull(deserialized.round());
        assertEquals(numberOfConsensusEvents, deserialized.numberOfConsensusEvents());
        assertEquals(timestamp, deserialized.consensusTimestamp());
        assertEquals(runningEventHash, deserialized.runningEventHash());
        assertEquals(minimumGenerationNonAncient, deserialized.minimumGenerationNonAncient());
        assertEquals(softwareVersion.toString(), deserialized.softwareVersion());
        assertNull(deserialized.wallClockTime());
        assertEquals(nodeId, deserialized.nodeId());
        assertEquals(signingNodes, deserialized.signingNodes());
        assertEquals(signingStakeSum, deserialized.signingStakeSum());
        assertEquals(totalStake, deserialized.totalStake());
    }
}
