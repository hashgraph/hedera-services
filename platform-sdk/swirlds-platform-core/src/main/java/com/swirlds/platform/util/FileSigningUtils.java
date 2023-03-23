/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.computeEntireHash;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.computeMetaHash;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.readFirstIntFromFile;
import static com.swirlds.common.stream.internal.TimestampStreamFileWriter.writeSignatureFile;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.stream.StreamType;
import com.swirlds.common.stream.internal.InvalidStreamFileException;
import com.swirlds.common.utility.ByteUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.Objects;

/**
 * This is a utility class to generate signature files
 * <p>
 * It can be used to sign v5 stream files in particular, or generally to sign any arbitrary file
 */
public final class FileSigningUtils {
    /**
     * Hidden constructor for utility class
     */
    private FileSigningUtils() {}

    /**
     * The suffix to append to file name when generating corresponding signature file
     */
    public static final String SIGNATURE_FILE_NAME_SUFFIX = "_sig";

    /**
     * Byte code indicating that the next 4 bytes are a signature length int, followed by a signature of that length
     */
    public static final byte TYPE_SIGNATURE = 3;

    /**
     * Byte code indicating that the next 48 bytes are the SHA-384 hash of the contents of the file to be signed
     */
    public static final byte TYPE_FILE_HASH = 4;

    /**
     * Supported stream version file
     */
    private static final int SUPPORTED_STREAM_FILE_VERSION = 5;

    /**
     * Type of the keyStore
     */
    private static final String KEYSTORE_TYPE = "pkcs12";

    /**
     * Accepts a file extension, and returns an all lower-case version of it, without a `.`
     *
     * @param originalExtension the original extension
     * @return the sanitized extension
     */
    @NonNull
    private static String sanitizeExtension(@NonNull final String originalExtension) {
        if (originalExtension.contains(".")) {
            return originalExtension
                    .substring(originalExtension.lastIndexOf(".") + 1)
                    .toLowerCase();
        } else {
            return originalExtension.toLowerCase();
        }
    }

    /**
     * Get the extension from a file name
     * <p>
     * Returns extension in all lowercase, without the `.`
     *
     * @param fileName the name of the file to get the extension of
     * @return the file extension, or null if the file has no extension
     */
    @Nullable
    private static String getFileExtension(@NonNull final String fileName) {
        if (fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        } else {
            return null;
        }
    }

    /**
     * Builds a signature file path from a destination directory and file name.
     * <p>
     * Creates the needed directories if they don't already exist
     *
     * @param destinationDirectory the directory to which the signature file is saved
     * @param sourceFile           file to be signed
     * @return signature file path
     */
    @NonNull
    private static String buildSignatureFilePath(
            @NonNull final File destinationDirectory, @NonNull final File sourceFile) {

        return new File(createDirectory(destinationDirectory), sourceFile.getName() + SIGNATURE_FILE_NAME_SUFFIX)
                .getPath();
    }

    /**
     * Creates a directory, if it doesn't already exist
     *
     * @param directory the desired destination directory
     * @return a File object representing the destination directory
     */
    @NonNull
    private static File createDirectory(@NonNull final File directory) {
        try {
            Files.createDirectories(directory.toPath());

            return directory;
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create directory", e);
        }
    }

    /**
     * Sign a byte array with a private key
     *
     * @param data    the data to be signed
     * @param keyPair the keyPair used for signing
     * @return the signature
     * @throws NoSuchAlgorithmException if an implementation of the required algorithm cannot be located or loaded
     * @throws NoSuchProviderException  thrown if the specified provider is not registered in the security provider
     *                                  list
     * @throws InvalidKeyException      thrown if the key is invalid
     * @throws SignatureException       thrown if this signature object is not initialized properly
     */
    @NonNull
    private static byte[] signData(@NonNull final byte[] data, @NonNull final KeyPair keyPair)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {

        final Signature signature =
                Signature.getInstance(SignatureType.RSA.signingAlgorithm(), SignatureType.RSA.provider());

        signature.initSign(keyPair.getPrivate());
        signature.update(data);

        return signature.sign();
    }

    /**
     * Loads a pfx key from file and returns the key pair
     *
     * @param keyFileName a pfx key file
     * @param password    the password for the key file
     * @param alias       alias of the key
     * @return a KeyPair
     */
    @NonNull
    public static KeyPair loadPfxKey(
            @NonNull final String keyFileName, @NonNull final String password, @NonNull final String alias) {

        Objects.requireNonNull(keyFileName, "keyFileName");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(alias, "alias");

        try (final FileInputStream inputStream = new FileInputStream(keyFileName)) {
            final KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            keyStore.load(inputStream, password.toCharArray());

            return new KeyPair(keyStore.getCertificate(alias).getPublicKey(), (PrivateKey)
                    keyStore.getKey(alias, password.toCharArray()));
        } catch (final NoSuchAlgorithmException
                | KeyStoreException
                | UnrecoverableKeyException
                | IOException
                | CertificateException e) {

            throw new RuntimeException("Unable to load Pfx key from file: " + keyFileName, e);
        }
    }

    /**
     * Sets up the constructable registry, and configures {@link SettingsCommon}
     * <p>
     * Should be called before using stream utilities
     */
    public static void initializeSystem() {
        BootstrapUtils.setupConstructableRegistry();

        // we don't want deserialization to fail based on any of these settings
        SettingsCommon.maxTransactionCountPerEvent = Integer.MAX_VALUE;
        SettingsCommon.maxTransactionBytesPerEvent = Integer.MAX_VALUE;
        SettingsCommon.transactionMaxBytes = Integer.MAX_VALUE;
        SettingsCommon.maxAddressSizeAllowed = Integer.MAX_VALUE;
    }

    /**
     * Generates a signature file for the given stream file
     *
     * @param destinationDirectory the directory to which the signature file will be saved
     * @param streamType           type of the stream file
     * @param streamFileToSign     the stream file to be signed
     * @param keyPair              the keyPair used for signing
     */
    public static void signStreamFile(
            @NonNull final File destinationDirectory,
            @NonNull final StreamType streamType,
            @NonNull final File streamFileToSign,
            @NonNull final KeyPair keyPair) {

        Objects.requireNonNull(destinationDirectory, "destinationDirectory must not be null");
        Objects.requireNonNull(streamType, "streamType must not be null");
        Objects.requireNonNull(streamFileToSign, "streamFileToSign must not be null");
        Objects.requireNonNull(keyPair, "keyPair must not be null");

        final String signatureFilePath = buildSignatureFilePath(destinationDirectory, streamFileToSign);

        if (!streamType.isStreamFile(streamFileToSign)) {
            System.err.println("File " + streamFileToSign + " is not a " + streamType);
            return;
        }

        try {
            final int version = readFirstIntFromFile(streamFileToSign);
            if (version != SUPPORTED_STREAM_FILE_VERSION) {
                System.err.printf(
                        "Failed to sign file [%s] with unsupported version [%s]%n",
                        streamFileToSign.getName(), version);
                return;
            }

            final Hash entireHash = computeEntireHash(streamFileToSign);
            final com.swirlds.common.crypto.Signature entireHashSignature = new com.swirlds.common.crypto.Signature(
                    SignatureType.RSA, signData(entireHash.getValue(), keyPair));

            final Hash metaHash = computeMetaHash(streamFileToSign, streamType);
            final com.swirlds.common.crypto.Signature metaHashSignature =
                    new com.swirlds.common.crypto.Signature(SignatureType.RSA, signData(metaHash.getValue(), keyPair));

            writeSignatureFile(
                    entireHash, entireHashSignature, metaHash, metaHashSignature, signatureFilePath, streamType);

            System.out.println("Generated signature file: " + signatureFilePath);
        } catch (final SignatureException | InvalidStreamFileException | IOException e) {
            System.err.println("Failed to sign file " + streamFileToSign.getName() + ". Exception: " + e);
        } catch (final InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException("Irrecoverable error encountered", e);
        }
    }

    /**
     * Generates a signature file for the given file
     * <p>
     * File types known to be signed via this method are: event stream v3, record stream v2, and account balance files.
     * However, any arbitrary file can be signed with this method
     * <p>
     * The written signature file contains the hash of the file to be signed, and a signature
     *
     * @param destinationDirectory the directory to which the signature file will be saved
     * @param fileToSign           the file to be signed
     * @param keyPair              the key pair used for signing
     */
    public static void signStandardFile(
            @NonNull final File destinationDirectory, @NonNull final File fileToSign, @NonNull final KeyPair keyPair) {

        Objects.requireNonNull(destinationDirectory, "destinationDirectory");
        Objects.requireNonNull(fileToSign, "fileToSign");
        Objects.requireNonNull(keyPair, "keyPair");

        final String signatureFilePath = buildSignatureFilePath(destinationDirectory, fileToSign);

        try (final FileOutputStream outputStream = new FileOutputStream(signatureFilePath, false)) {
            final byte[] fileHash = computeEntireHash(fileToSign).getValue();
            final byte[] signature = signData(fileHash, keyPair);

            outputStream.write(TYPE_FILE_HASH);
            outputStream.write(fileHash);

            outputStream.write(TYPE_SIGNATURE);
            outputStream.write(ByteUtils.intToByteArray(signature.length));
            outputStream.write(signature);

            System.out.println("Generated signature file: " + signatureFilePath);
        } catch (final SignatureException | IOException e) {
            System.err.println("Failed to sign file " + fileToSign.getName() + ". Exception: " + e);
        } catch (final InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException("Irrecoverable error encountered", e);
        }
    }

    /**
     * Signs all stream files of specified types in a directory
     * <p>
     * If a recoverable error is encountered while signing an individual file in the directory, an error will be logged,
     * and signing of remaining files will continue
     *
     * @param sourceDirectory      the source directory
     * @param destinationDirectory the destination directory
     * @param streamTypes          the types of stream files to sign
     * @param keyPair              the key pair to sign with
     */
    public static void signStreamFilesInDirectory(
            @NonNull final File sourceDirectory,
            @NonNull final File destinationDirectory,
            @NonNull final Collection<StreamType> streamTypes,
            @NonNull final KeyPair keyPair) {

        Objects.requireNonNull(sourceDirectory, "sourceDirectory");
        Objects.requireNonNull(destinationDirectory, "destinationDirectory");
        Objects.requireNonNull(streamTypes, "streamTypes");
        Objects.requireNonNull(keyPair, "keyPair");

        for (final StreamType streamType : streamTypes) {
            final File[] sourceFiles =
                    sourceDirectory.listFiles((directory, fileName) -> streamType.isStreamFile(fileName));

            if (sourceFiles == null) {
                throw new RuntimeException("Failed to list files in directory: " + sourceDirectory);
            }

            for (final File file : sourceFiles) {
                signStreamFile(destinationDirectory, streamType, file, keyPair);
            }
        }
    }

    /**
     * Signs all files of specified extension types in a directory
     * <p>
     * If a recoverable error is encountered while signing an individual file in the directory, an error will be logged,
     * and signing of remaining files will continue
     *
     * @param sourceDirectory      the source directory
     * @param destinationDirectory the destination directory
     * @param extensionTypes       the extensions of file types to be signed
     * @param keyPair              the key pair to sign with
     */
    public static void signStandardFilesInDirectory(
            @NonNull final File sourceDirectory,
            @NonNull final File destinationDirectory,
            @NonNull final Collection<String> extensionTypes,
            @NonNull final KeyPair keyPair) {

        Objects.requireNonNull(sourceDirectory, "sourceDirectory");
        Objects.requireNonNull(destinationDirectory, "destinationDirectory");
        Objects.requireNonNull(extensionTypes, "extensionTypes");
        Objects.requireNonNull(keyPair, "keyPair");

        final Collection<String> sanitizedExtensionTypes = extensionTypes.stream()
                .filter(Objects::nonNull)
                .map(FileSigningUtils::sanitizeExtension)
                .toList();

        final File[] sourceFiles = sourceDirectory.listFiles((directory, fileName) -> {
            final String fileExtension = getFileExtension(fileName);

            if (fileExtension == null) {
                return false;
            }

            return sanitizedExtensionTypes.contains(fileExtension);
        });

        if (sourceFiles == null) {
            throw new RuntimeException("Failed to list files in directory: " + sourceDirectory);
        }

        for (final File file : sourceFiles) {
            signStandardFile(destinationDirectory, file, keyPair);
        }
    }
}
