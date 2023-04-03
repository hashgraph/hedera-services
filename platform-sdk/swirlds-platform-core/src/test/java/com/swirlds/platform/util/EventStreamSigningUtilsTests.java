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

package com.swirlds.platform.util;

import static com.swirlds.common.stream.internal.LinkedObjectStreamValidateUtils.validateFileAndSignature;
import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.recovery.RecoveryTestUtils.generateRandomEvents;
import static com.swirlds.platform.recovery.RecoveryTestUtils.writeRandomEventStream;
import static com.swirlds.platform.util.EventStreamSigningUtils.initializeSystem;
import static com.swirlds.platform.util.EventStreamSigningUtils.signEventStreamFile;
import static com.swirlds.platform.util.FileSigningUtils.SIGNATURE_FILE_NAME_SUFFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.stream.EventStreamType;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.recovery.RecoveryTestUtils;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link EventStreamSigningUtils}
 */
class EventStreamSigningUtilsTests {
    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    private Path testDirectoryPath;

    /**
     * Directory containing stream files to sign
     */
    private Path toSignDirectory;

    /**
     * The directory where the signature files will be written
     */
    private Path destinationDirectory;

    /**
     * The key to use for tests
     */
    private final KeyPair keyPair = loadKey();

    /**
     * Sets up for each test
     */
    @BeforeEach
    void setup() {
        destinationDirectory = testDirectoryPath.resolve("signatureFiles");

        // the utility method being leveraged saves stream files to a directory "events_test"
        toSignDirectory = testDirectoryPath.resolve("events_test");
    }

    /**
     * Loads a key from a keystore in the test resources
     *
     * @return the key
     */
    private static KeyPair loadKey() {
        try {
            return FileSigningUtils.loadPfxKey(
                    Path.of(Objects.requireNonNull(
                                    EventStreamSigningUtilsTests.class.getResource("testKeyStore.pkcs12"))
                            .toURI()),
                    "123456",
                    "testKey");
        } catch (final URISyntaxException e) {
            throw new RuntimeException("Failed to get resource", e);
        }
    }

    /**
     * Create some event stream files in the toSignDirectory
     */
    private void createStreamFiles() {
        initializeSystem();

        final Random random = getRandomPrintSeed();

        final List<EventImpl> events = generateRandomEvents(random, 100L, Duration.ofSeconds(10), 1, 5);

        try {
            writeRandomEventStream(random, testDirectoryPath, 2, events);
        } catch (final NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("failed to write event stream files", e);
        }
    }

    /**
     * Gets a list of the regular files in the destination directory
     *
     * @return the files in the destination directory
     */
    private List<Path> getDestinationDirectoryFiles() {
        try (final Stream<Path> stream = Files.walk(destinationDirectory)) {
            return stream.filter(Files::isRegularFile).toList();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to list files in directory: " + destinationDirectory, e);
        }
    }

    @Test
    @DisplayName("Sign stream file")
    void signSingleStreamFile() {
        createStreamFiles();

        final Path fileToSign;
        try {
            // since we are only signing 1 file, just grab the middle one
            fileToSign = RecoveryTestUtils.getMiddleEventStreamFile(toSignDirectory);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        assertTrue(
                signEventStreamFile(
                        FileSigningUtils.buildSignatureFilePath(destinationDirectory, fileToSign), fileToSign, keyPair),
                "Signing failed");

        final List<Path> destinationDirectoryFiles = getDestinationDirectoryFiles();

        assertNotNull(destinationDirectoryFiles, "Expected signature file to be created");
        assertEquals(1, destinationDirectoryFiles.size(), "Expected one signature file to be created");

        final Path signatureFile = destinationDirectoryFiles.get(0);

        assertEquals(
                fileToSign.getFileName() + SIGNATURE_FILE_NAME_SUFFIX,
                signatureFile.getFileName().toString(),
                "Expected signature file to have the same name as the file to sign, with _sig appended");

        // validate the stream file and sig file via the standard method
        validateFileAndSignature(
                fileToSign.toFile(), signatureFile.toFile(), keyPair.getPublic(), EventStreamType.getInstance());
    }
}
