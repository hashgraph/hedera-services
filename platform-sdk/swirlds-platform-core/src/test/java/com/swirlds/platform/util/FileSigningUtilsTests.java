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

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.utility.ByteUtils.byteArrayToInt;
import static com.swirlds.platform.recovery.RecoveryTestUtils.generateRandomEvents;
import static com.swirlds.platform.recovery.RecoveryTestUtils.writeRandomEventStream;
import static com.swirlds.platform.util.FileSigningUtils.SIGNATURE_FILE_NAME_SUFFIX;
import static com.swirlds.platform.util.FileSigningUtils.initializeSystem;
import static com.swirlds.platform.util.FileSigningUtils.signStandardFile;
import static com.swirlds.platform.util.FileSigningUtils.signStandardFilesInDirectory;
import static com.swirlds.platform.util.FileSigningUtils.signStreamFile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.stream.LinkedObjectStreamUtilities;
import com.swirlds.common.stream.internal.LinkedObjectStreamValidateUtils;
import com.swirlds.common.test.stream.TestStreamType;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.recovery.RecoveryTestUtils;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;
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
        try (final DataOutputStream output =
                new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            output.writeChars(content);
        } catch (final IOException e) {
            throw new RuntimeException("unable to write to file: " + file, e);
        }
    }

    /**
     * Loads a key from a keystore in the test resources
     *
     * @return the key
     */
    private KeyPair loadKey() {
        return FileSigningUtils.loadPfxKey(
                Objects.requireNonNull(FileSigningUtilsTests.class.getResource("testKeyStore.pkcs12"))
                        .getFile(),
                "123456",
                "testKey");
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
     * Asserts that a signature file contains the expected contents for a given original file and signing keyPair
     *
     * @param originalFile  the original file that was signed
     * @param signatureFile the signature file that was created
     * @param keyPair       the keyPair that was used to sign the file
     */
    private void assertStandardSignatureFileValidity(
            final File originalFile, final File signatureFile, final KeyPair keyPair) {

        try (FileInputStream signatureFileInputStream = new FileInputStream(signatureFile)) {

            assertEquals(
                    FileSigningUtils.TYPE_FILE_HASH,
                    signatureFileInputStream.read(),
                    "First byte of sig file should be the file hash byte code");

            // the full hash of the file that was signed
            final byte[] originalFileHash =
                    LinkedObjectStreamUtilities.computeEntireHash(originalFile).getValue();

            assertArrayEquals(
                    originalFileHash,
                    signatureFileInputStream.readNBytes(originalFileHash.length),
                    "unexpected file hash in signature file");

            assertEquals(
                    FileSigningUtils.TYPE_SIGNATURE,
                    signatureFileInputStream.read(),
                    "Next byte of sig file should the signature byte code");

            assertEquals(
                    SignatureType.RSA.signatureLength(),
                    byteArrayToInt(signatureFileInputStream.readNBytes(Integer.BYTES), 0),
                    "Next 4 bytes of sig file should represent an int which is the length of an RSA signature");

            // generate new signature, to compare against what is actually in the sig file
            final Signature signature =
                    Signature.getInstance(SignatureType.RSA.signingAlgorithm(), SignatureType.RSA.provider());
            signature.initSign(keyPair.getPrivate());
            signature.update(originalFileHash);

            assertArrayEquals(
                    signature.sign(),
                    signatureFileInputStream.readNBytes(SignatureType.RSA.signatureLength()),
                    "unexpected signature in signature file");

            assertEquals(
                    0,
                    signatureFileInputStream.readAllBytes().length,
                    "signature file contains unexpected extra bytes");
        } catch (final IOException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException("test failure", e);
        } catch (final SignatureException | InvalidKeyException e) {
            throw new RuntimeException("signing failure during sig file verification", e);
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

        final KeyPair keyPair = loadKey();

        signStandardFile(destinationDirectory, fileToSign, keyPair);

        final File[] destinationDirectoryFiles = destinationDirectory.listFiles();

        assertNotNull(destinationDirectoryFiles, "Expected signature file to be created");
        assertEquals(1, destinationDirectoryFiles.length, "Expected one signature file to be created");
        assertEquals(
                fileToSign.getName() + SIGNATURE_FILE_NAME_SUFFIX,
                destinationDirectoryFiles[0].getName(),
                "Expected signature file to have the same name as the file to sign, with _sig appended");

        assertStandardSignatureFileValidity(fileToSign, destinationDirectoryFiles[0], keyPair);
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

        final Collection<String> filesNamesToSign =
                List.of("fileToSign1.txt", "fileToSign2.TXT", "fileToSign3.arbiTRARY", "fileToSign4.z");

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

        final KeyPair keyPair = loadKey();

        signStandardFilesInDirectory(
                toSignDirectoryPath.toFile(), destinationDirectory, List.of(".txt", "arbitrary", ".Z"), keyPair);

        final Collection<File> destinationDirectoryFiles = Arrays.stream(
                        Objects.requireNonNull(destinationDirectory.listFiles()))
                .toList();

        assertNotNull(destinationDirectoryFiles, "Expected signature files to be created");
        assertEquals(filesNamesToSign.size(), destinationDirectoryFiles.size(), "Incorrect number of sig files");

        for (final String originalFileName : filesNamesToSign) {
            final File expectedFile = destinationDirectory
                    .toPath()
                    .resolve(originalFileName + SIGNATURE_FILE_NAME_SUFFIX)
                    .toFile();

            assertTrue(
                    destinationDirectoryFiles.contains(expectedFile),
                    "Expected signature file to be created for " + originalFileName);

            assertStandardSignatureFileValidity(
                    toSignDirectoryPath.resolve(originalFileName).toFile(), expectedFile, keyPair);
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
    void signStreamFilesInDirectory() {
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
        FileSigningUtils.signStreamFilesInDirectory(
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
