package com.swirlds.platform.util;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.recovery.RecoveryTestUtils.generateRandomEvents;
import static com.swirlds.platform.recovery.RecoveryTestUtils.writeRandomEventStream;
import static com.swirlds.platform.util.FileSigningUtils.initializeSystem;
import static com.swirlds.platform.util.FileSigningUtils.signStandardFile;
import static com.swirlds.platform.util.FileSigningUtils.signStandardFilesInDirectory;
import static com.swirlds.platform.util.FileSigningUtils.signStreamFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.test.stream.TestStreamType;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.recovery.RecoveryTestUtils;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
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
 * Tests for {@link FileSigningUtils}
 */
class FileSigningUtilsTests {
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
     * The name of the test keystore in the test resources
     */
    private final String keyStoreFileName = "testKeyStore.p12";

    /**
     * The password used when generating the test keystore
     */
    private final String password = "123456";

    /**
     * The alias used when generating the test keystore
     */
    private final String keyAlias = "testKey";

    /**
     * Source of randomness for the tests
     */
    private Random random;

    /**
     * Creates a file with string contents
     *
     * @param file    the file to create
     * @param content the contents of the file
     */
    private static void createStandardFile(final File file, final String content) {
        try (final DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {
            output.writeChars(content);
        } catch (final IOException e) {
            throw new RuntimeException("unable to write to file: " + file, e);
        }
    }

    /**
     * Loads a key from a keystore in the test resources
     *
     * @param keyStoreFileName the name of the keystore file
     * @param password         the password for the keystore
     * @param alias            the alias of the key to load
     * @return the key
     */
    private static KeyPair loadKey(final String keyStoreFileName, final String password, final String alias) {
        return FileSigningUtils.loadPfxKey(
                Objects.requireNonNull(FileSigningUtilsTests.class.getResource(keyStoreFileName)).getFile(),
                password,
                alias);
    }

    /**
     * Create some event stream files in the toSignDirectory
     */
    private void createStreamFiles() {
        initializeSystem();

        final List<EventImpl> events = generateRandomEvents(random, 100L, Duration.ofSeconds(10), 1, 5);

        try {
            writeRandomEventStream(random, testDirectoryPath, 2, events);
        } catch (final NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("failed to write event stream files", e);
        }
    }

    /**
     * Sets up for each test
     */
    @BeforeEach
    void setup() {
        destinationDirectory = testDirectoryPath.resolve("signatureFiles").toFile();
        random = getRandomPrintSeed();
    }

    @Test
    @DisplayName("Sign arbitrary file")
    void signArbitraryFile() {
        final File fileToSign = testDirectoryPath.resolve("fileToSign.txt").toFile();
        final String fileContents = "Hello there";

        createStandardFile(fileToSign, fileContents);

        signStandardFile(destinationDirectory, fileToSign, loadKey(keyStoreFileName, password, keyAlias));

        final File[] destinationDirectoryFiles = destinationDirectory.listFiles();

        assertNotNull(destinationDirectoryFiles, "Expected signature file to be created");
        assertEquals(1, destinationDirectoryFiles.length, "Expected one signature file to be created");
        assertEquals(fileToSign.getName() + "_sig", destinationDirectoryFiles[0].getName(),
                "Expected signature file to have the same name as the file to sign, with _sig appended");

        // TODO read sig file and verify it has correct contents
    }

    @Test
    @DisplayName("Sign arbitrary files in directory")
    void signArbitraryFilesInDirectory() {
        // the directory where we will place files we want to sign
        final Path toSignDirectoryPath = testDirectoryPath.resolve("dirToSign");
        try {
            Files.createDirectories(toSignDirectoryPath);
        } catch (final IOException e) {
            throw new RuntimeException("unable to create toSign directory", e);
        }

        final Collection<String> filesNamesToSign = List.of(
                "fileToSign1.txt",
                "fileToSign2.TXT",
                "fileToSign3.arbiTRARY",
                "fileToSign4.z");

        // create files to sign
        for (final String fileName : filesNamesToSign) {
            createStandardFile(toSignDirectoryPath.resolve(fileName).toFile(), "Hello there " + fileName);
        }

        // Create some various files to *not* sign
        final String content = "Not signed";
        createStandardFile(toSignDirectoryPath.resolve("fileNotToSign1.exe").toFile(), content);
        createStandardFile(toSignDirectoryPath.resolve("fileNotToSign2").toFile(), content);
        createStandardFile(toSignDirectoryPath.resolve("fileNotToSign3txt").toFile(), content);
        try {
            // create a file in a nested directory, which will also not be signed
            final Path nestedDirectoryPath = Files.createDirectories(toSignDirectoryPath.resolve("nestedDir"));
            createStandardFile(nestedDirectoryPath.resolve("fileNotToSign4.txt").toFile(), content);
        } catch (final IOException e) {
            throw new RuntimeException("unable to create nested directory", e);
        }

        signStandardFilesInDirectory(toSignDirectoryPath.toFile(), destinationDirectory,
                List.of(".txt", "arbitrary", ".Z"), loadKey(keyStoreFileName, password, keyAlias));

        final Collection<File> destinationDirectoryFiles = Arrays.stream(
                Objects.requireNonNull(destinationDirectory.listFiles())).toList();

        assertNotNull(destinationDirectoryFiles, "Expected signature files to be created");
        assertEquals(filesNamesToSign.size(), destinationDirectoryFiles.size(), "Incorrect number of sig files");

        for (final String originalFileName : filesNamesToSign) {
            final File expectedFile = destinationDirectory.toPath().resolve(originalFileName + "_sig").toFile();

            assertTrue(destinationDirectoryFiles.contains(expectedFile),
                    "Expected signature file to be created for " + originalFileName);

            // TODO read sig file and verify it has correct contents
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final File fileToSign = fileToSignPath.toFile();
        assertNotNull(fileToSign, "Expected to find a file to sign");

        signStreamFile(destinationDirectory, EventStreamType.getInstance(), fileToSign,
                loadKey(keyStoreFileName, password, keyAlias));

        final File[] destinationDirectoryFiles = destinationDirectory.listFiles();

        assertNotNull(destinationDirectoryFiles, "Expected signature file to be created");
        assertEquals(1, destinationDirectoryFiles.length, "Expected one signature file to be created");
        assertEquals(fileToSign.getName() + "_sig", destinationDirectoryFiles[0].getName(),
                "Expected signature file to have the same name as the file to sign, with _sig appended");

        // TODO read sig file and verify it has correct contents
    }

    @Test
    @DisplayName("Sign stream files in directory")
    void signStreamFilesInDirectory() {
        createStreamFiles();

        // the utility method being leveraged saves stream files to a directory "events_test"
        final Path toSignDirectoryPath = testDirectoryPath.resolve("events_test");

        // find out how many event stream files were written in the createStreamFiles method
        final int numFilesToSign = Objects.requireNonNull(toSignDirectoryPath.toFile()
                .listFiles((directory, fileName) -> fileName.endsWith(".evts"))).length;

        // pass in stream types EventStreamType and TestStreamType. There aren't any TestStream files in the directory,
        // so this covers that edge case
        FileSigningUtils.signStreamFilesInDirectory(toSignDirectoryPath.toFile(), destinationDirectory,
                List.of(EventStreamType.getInstance(), TestStreamType.TEST_STREAM),
                loadKey(keyStoreFileName, password, keyAlias));

        final File[] destinationDirectoryFiles = destinationDirectory.listFiles();

        assertNotNull(destinationDirectoryFiles, "Expected signature file to be created");
        assertEquals(numFilesToSign, destinationDirectoryFiles.length,
                "Expected correct number of signature files to be created");

        // TODO read sig file and verify it has correct contents
    }
}
