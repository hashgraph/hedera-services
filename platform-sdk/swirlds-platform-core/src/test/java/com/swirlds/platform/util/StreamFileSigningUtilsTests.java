package com.swirlds.platform.util;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.recovery.RecoveryTestUtils.generateRandomEvents;
import static com.swirlds.platform.recovery.RecoveryTestUtils.writeRandomEventStream;
import static com.swirlds.platform.util.FileSigningUtils.SIGNATURE_FILE_NAME_SUFFIX;
import static com.swirlds.platform.util.SigningTestUtils.loadKey;
import static com.swirlds.platform.util.StreamFileSigningUtils.initializeSystem;
import static com.swirlds.platform.util.StreamFileSigningUtils.signStreamFile;
import static com.swirlds.platform.util.StreamFileSigningUtils.signStreamFilesInDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.stream.internal.LinkedObjectStreamValidateUtils;
import com.swirlds.common.test.stream.TestStreamType;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.recovery.RecoveryTestUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link StreamFileSigningUtils}
 */
class StreamFileSigningUtilsTests {
    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    private Path testDirectoryPath;

    /**
     * The directory where the signature files will be written
     */
    private File destinationDirectory;

    /**
     * Sets up for each test
     */
    @BeforeEach
    void setup() {
        destinationDirectory = testDirectoryPath.resolve("signatureFiles").toFile();
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

    @Test
    @DisplayName("Sign stream file")
    void signSingleStreamFile() {
        createStreamFiles();

        // the utility method being leveraged saves stream files to a directory "events_test"
        final Path toSignDirectoryPath = testDirectoryPath.resolve("events_test");

        final Path fileToSignPath;
        try {
            // since we are only signing 1 file, just grab the middle one
            fileToSignPath = RecoveryTestUtils.getMiddleEventStreamFile(toSignDirectoryPath);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        final File fileToSign = fileToSignPath.toFile();
        assertNotNull(fileToSign, "Expected to find a file to sign");

        final KeyPair keyPair = loadKey();

        signStreamFile(destinationDirectory, EventStreamType.getInstance(), fileToSign, keyPair);

        final File[] destinationDirectoryFiles = destinationDirectory.listFiles();

        assertNotNull(destinationDirectoryFiles, "Expected signature file to be created");
        assertEquals(1, destinationDirectoryFiles.length, "Expected one signature file to be created");
        assertEquals(
                fileToSign.getName() + SIGNATURE_FILE_NAME_SUFFIX,
                destinationDirectoryFiles[0].getName(),
                "Expected signature file to have the same name as the file to sign, with _sig appended");

        // validate the stream file and sig file via the standard method
        LinkedObjectStreamValidateUtils.validateFileAndSignature(
                fileToSign, destinationDirectoryFiles[0], keyPair.getPublic(), EventStreamType.getInstance());
    }

    @Test
    @DisplayName("Sign stream files in directory")
    void signStreamFiles() {
        createStreamFiles();

        // the utility method being leveraged saves stream files to a directory "events_test"
        final Path toSignDirectoryPath = testDirectoryPath.resolve("events_test");

        // find out which files have been created that will be signed
        final Collection<File> filesToSign = Arrays.stream(Objects.requireNonNull(
                        toSignDirectoryPath.toFile().listFiles((directory, fileName) -> fileName.endsWith(".evts"))))
                .toList();

        final KeyPair keyPair = loadKey();

        // pass in stream types EventStreamType and TestStreamType. There aren't any TestStream files in the directory,
        // so this covers the edge case of trying to sign a stream type that doesn't exist in the directory
        signStreamFilesInDirectory(
                toSignDirectoryPath.toFile(),
                destinationDirectory,
                List.of(EventStreamType.getInstance(), TestStreamType.TEST_STREAM),
                keyPair);

        final Collection<File> destinationDirectoryFiles = Arrays.stream(
                        Objects.requireNonNull(destinationDirectory.listFiles()))
                .toList();

        assertNotNull(destinationDirectoryFiles, "Expected signature file to be created");
        assertEquals(
                filesToSign.size(),
                destinationDirectoryFiles.size(),
                "Expected correct number of signature files to be created");

        for (final File originalFile : filesToSign) {
            final File expectedFile = destinationDirectory
                    .toPath()
                    .resolve(originalFile.getName() + SIGNATURE_FILE_NAME_SUFFIX)
                    .toFile();

            assertTrue(
                    destinationDirectoryFiles.contains(expectedFile),
                    "Expected signature file to be created for " + originalFile.getName());

            // validate the stream file and sig file via the standard method
            LinkedObjectStreamValidateUtils.validateFileAndSignature(
                    originalFile, expectedFile, keyPair.getPublic(), EventStreamType.getInstance());
        }
    }
}
