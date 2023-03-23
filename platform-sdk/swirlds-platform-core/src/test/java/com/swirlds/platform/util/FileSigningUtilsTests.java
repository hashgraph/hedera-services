package com.swirlds.platform.util;

import static com.swirlds.platform.util.FileSigningUtils.signStandardFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
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
    private Path testDirectory;

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
        destinationDirectory = testDirectory.resolve("signatureFiles").toFile();
    }

    @Test
    @DisplayName("Sign arbitrary file")
    void signArbitraryFile() {
        final File fileToSign = testDirectory.resolve("fileToSign.txt").toFile();
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
}
