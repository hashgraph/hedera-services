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

import static com.swirlds.common.utility.ByteUtils.byteArrayToInt;
import static com.swirlds.platform.util.FileSigningUtils.SIGNATURE_FILE_NAME_SUFFIX;
import static com.swirlds.platform.util.SigningTestUtils.loadKey;
import static com.swirlds.platform.util.StandardFileSigningUtils.TYPE_FILE_HASH;
import static com.swirlds.platform.util.StandardFileSigningUtils.TYPE_SIGNATURE;
import static com.swirlds.platform.util.StandardFileSigningUtils.signStandardFile;
import static com.swirlds.platform.util.StandardFileSigningUtils.signStandardFilesInDirectory;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.stream.LinkedObjectStreamUtilities;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link StandardFileSigningUtils}
 */
class StandardFileSigningUtilsTests {
    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    private Path testDirectoryPath;

    /**
     * The directory where the signature files will be written
     */
    private Path destinationDirectory;

    /**
     * Sets up for each test
     */
    @BeforeEach
    void setup() {
        destinationDirectory = testDirectoryPath.resolve("signatureFiles");
    }

    /**
     * Creates a file with string contents
     *
     * @param file    the file to create
     * @param content the contents of the file
     */
    private static void createStandardFile(final Path file, final String content) {
        try (final DataOutputStream output =
                new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            output.writeChars(content);
        } catch (final IOException e) {
            throw new RuntimeException("unable to write to file: " + file, e);
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
            final Path originalFile, final Path signatureFile, final KeyPair keyPair) {

        try (InputStream signatureFileInputStream = Files.newInputStream(signatureFile)) {
            assertEquals(
                    TYPE_FILE_HASH,
                    signatureFileInputStream.read(),
                    "First byte of sig file should be the file hash byte code");

            // the full hash of the file that was signed
            final byte[] originalFileHash = LinkedObjectStreamUtilities.computeEntireHash(originalFile.toFile())
                    .getValue();

            assertArrayEquals(
                    originalFileHash,
                    signatureFileInputStream.readNBytes(originalFileHash.length),
                    "unexpected file hash in signature file");

            assertEquals(
                    TYPE_SIGNATURE,
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
        } catch (final IOException
                | NoSuchAlgorithmException
                | NoSuchProviderException
                | SignatureException
                | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Sign arbitrary file")
    void signArbitraryFile() {
        final Path fileToSign = testDirectoryPath.resolve("fileToSign.txt");
        createStandardFile(fileToSign, "Hello there");

        final KeyPair keyPair = loadKey();

        signStandardFile(destinationDirectory, fileToSign, keyPair);

        final List<Path> destinationDirectoryFiles;
        try (final Stream<Path> stream = Files.walk(destinationDirectory)) {
            destinationDirectoryFiles = stream.filter(Files::isRegularFile).toList();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to list files in directory: " + destinationDirectory, e);
        }

        assertNotNull(destinationDirectoryFiles, "Expected signature file to be created");
        assertEquals(1, destinationDirectoryFiles.size(), "Expected one signature file to be created");
        assertEquals(
                fileToSign.getFileName() + SIGNATURE_FILE_NAME_SUFFIX,
                destinationDirectoryFiles.get(0).getFileName().toString(),
                "Expected signature file to have the same name as the file to sign, with _sig appended");

        assertStandardSignatureFileValidity(fileToSign, destinationDirectoryFiles.get(0), keyPair);
    }

    @Test
    @DisplayName("Sign standard files in directory")
    void signStandardFiles() {
        // the directory where we will place files we want to sign
        final Path toSignDirectoryPath = testDirectoryPath.resolve("dirToSign");
        try {
            Files.createDirectories(toSignDirectoryPath);
        } catch (final IOException e) {
            throw new RuntimeException("unable to create dirToSign directory", e);
        }

        final Collection<String> filesNamesToSign =
                List.of("fileToSign1.txt", "fileToSign2.TXT", "fileToSign3.arbiTRARY", "fileToSign4.z");

        // create files to sign
        for (final String fileName : filesNamesToSign) {
            createStandardFile(toSignDirectoryPath.resolve(fileName), "Hello there " + fileName);
        }

        // Create some various files to *not* sign
        final String content = "Not signed";
        createStandardFile(toSignDirectoryPath.resolve("fileNotToSign1.exe"), content);
        createStandardFile(toSignDirectoryPath.resolve("fileNotToSign2"), content);
        createStandardFile(toSignDirectoryPath.resolve("fileNotToSign3txt"), content);

        final KeyPair keyPair = loadKey();

        signStandardFilesInDirectory(
                toSignDirectoryPath, destinationDirectory, List.of(".txt", "arbitrary", ".Z"), keyPair);

        final List<Path> destinationDirectoryFiles;
        try (final Stream<Path> stream = Files.walk(destinationDirectory)) {
            destinationDirectoryFiles = stream.filter(Files::isRegularFile).toList();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to list files in directory: " + destinationDirectory, e);
        }

        assertNotNull(destinationDirectoryFiles, "Expected signature files to be created");
        assertEquals(filesNamesToSign.size(), destinationDirectoryFiles.size(), "Incorrect number of sig files");

        for (final String originalFileName : filesNamesToSign) {
            final Path expectedFile = destinationDirectory.resolve(originalFileName + SIGNATURE_FILE_NAME_SUFFIX);

            assertTrue(
                    destinationDirectoryFiles.contains(expectedFile),
                    "Expected signature file to be created for " + originalFileName);

            assertStandardSignatureFileValidity(toSignDirectoryPath.resolve(originalFileName), expectedFile, keyPair);
        }
    }
}
