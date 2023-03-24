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

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.computeEntireHash;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.computeMetaHash;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.readFirstIntFromFile;
import static com.swirlds.common.stream.internal.TimestampStreamFileWriter.writeSignatureFile;
import static com.swirlds.platform.util.FileSigningUtils.buildSignatureFilePath;
import static com.swirlds.platform.util.FileSigningUtils.signData;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.stream.internal.InvalidStreamFileException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Utility class for signing event stream files
 */
public class EventStreamSigningUtils {
    /**
     * Hidden constructor
     */
    private EventStreamSigningUtils() {}

    /**
     * Supported stream version file
     */
    private static final int SUPPORTED_STREAM_FILE_VERSION = 5;

    /**
     * Sets up the constructable registry, and configures {@link SettingsCommon}
     * <p>
     * Should be called before using stream utilities
     * <p>
     * Do <strong>NOT</strong> call this during standard node operation, as it will interfere with static settings
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
     * Generates a signature file for the given event stream file
     *
     * @param destinationDirectory the directory where the signature file will be saved
     * @param streamFileToSign     the stream file to be signed
     * @param keyPair              the keyPair used for signing
     */
    public static void signEventStreamFile(
            @NonNull final Path destinationDirectory,
            @NonNull final Path streamFileToSign,
            @NonNull final KeyPair keyPair) {

        Objects.requireNonNull(destinationDirectory, "destinationDirectory must not be null");
        Objects.requireNonNull(streamFileToSign, "streamFileToSign must not be null");
        Objects.requireNonNull(keyPair, "keyPair must not be null");

        try {
            final int version = readFirstIntFromFile(streamFileToSign.toFile());
            if (version != SUPPORTED_STREAM_FILE_VERSION) {
                System.err.printf(
                        "Failed to sign file [%s] with unsupported version [%s]%n",
                        streamFileToSign.getFileName(), version);
                return;
            }

            final Hash entireHash = computeEntireHash(streamFileToSign.toFile());
            final com.swirlds.common.crypto.Signature entireHashSignature = new com.swirlds.common.crypto.Signature(
                    SignatureType.RSA, signData(entireHash.getValue(), keyPair));

            final EventStreamType streamType = EventStreamType.getInstance();

            final Hash metaHash = computeMetaHash(streamFileToSign.toFile(), streamType);
            final com.swirlds.common.crypto.Signature metaHashSignature =
                    new com.swirlds.common.crypto.Signature(SignatureType.RSA, signData(metaHash.getValue(), keyPair));

            final Path signatureFilePath = buildSignatureFilePath(destinationDirectory, streamFileToSign);

            writeSignatureFile(
                    entireHash,
                    entireHashSignature,
                    metaHash,
                    metaHashSignature,
                    signatureFilePath.toString(),
                    streamType);

            System.out.println("Generated signature file: " + signatureFilePath);
        } catch (final SignatureException | InvalidStreamFileException | IOException e) {
            System.err.println("Failed to sign file " + streamFileToSign.getFileName() + ". Exception: " + e);
        } catch (final InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException("Irrecoverable error encountered", e);
        }
    }

    /**
     * Signs all event stream files in a directory
     * <p>
     * If a recoverable error is encountered while signing an individual file in the directory, an error will be logged,
     * and signing of remaining files will continue
     *
     * @param sourceDirectory      the source directory
     * @param destinationDirectory the destination directory
     * @param keyPair              the key pair to sign with
     */
    public static void signEventStreamFilesInDirectory(
            @NonNull final Path sourceDirectory,
            @NonNull final Path destinationDirectory,
            @NonNull final KeyPair keyPair) {

        Objects.requireNonNull(sourceDirectory, "sourceDirectory must not be null");
        Objects.requireNonNull(destinationDirectory, "destinationDirectory must not be null");
        Objects.requireNonNull(keyPair, "keyPair must not be null");

        final EventStreamType streamType = EventStreamType.getInstance();

        try (final Stream<Path> stream = Files.walk(sourceDirectory)) {
            stream.filter(filePath -> streamType.isStreamFile(filePath.toString()))
                    .forEach(path -> signEventStreamFile(destinationDirectory, path, keyPair));
        } catch (final IOException e) {
            throw new RuntimeException("Failed to list files in directory: " + sourceDirectory);
        }
    }
}
