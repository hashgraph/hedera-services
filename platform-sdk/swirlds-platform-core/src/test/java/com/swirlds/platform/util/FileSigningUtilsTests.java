package com.swirlds.platform.util;

import static com.swirlds.platform.util.FileSigningUtils.signStandardFile;
import static com.swirlds.platform.util.FileSigningUtils.signStandardFilesInDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
     * The directory containing files to sign
     */
    private Path toSignDirectoryPath;

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
                FileSigningUtilsTests.class.getResource(keyStoreFileName).getFile(), password, alias);
    }

    /**
     * Sets up for each test
     */
    @BeforeEach
    void setup() {
        destinationDirectory = testDirectoryPath.resolve("signatureFiles").toFile();

        try {
            toSignDirectoryPath = Files.createDirectories(testDirectoryPath.resolve("dirToSign"));
        } catch (final IOException e) {
            throw new RuntimeException("unable to create toSign directory", e);
        }
    }

    @Test
    @DisplayName("Sign arbitrary file")
    void signArbitraryFile() {
        final File fileToSign = toSignDirectoryPath.resolve("fileToSign.txt").toFile();
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

        final Collection<File> destinationDirectoryFiles = Arrays.stream(destinationDirectory.listFiles()).toList();

        assertNotNull(destinationDirectoryFiles, "Expected signature files to be created");
        assertEquals(filesNamesToSign.size(), destinationDirectoryFiles.size(), "Incorrect number of sig files");

        for (final String originalFileName : filesNamesToSign) {
            final File expectedFile = destinationDirectory.toPath().resolve(originalFileName + "_sig").toFile();

            assertTrue(destinationDirectoryFiles.contains(expectedFile),
                    "Expected signature file to be created for " + originalFileName);

            // TODO read sig file and verify it has correct contents
        }
    }
}
