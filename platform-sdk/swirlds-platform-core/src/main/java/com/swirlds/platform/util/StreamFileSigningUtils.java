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
import com.swirlds.common.stream.StreamType;
import com.swirlds.common.stream.internal.InvalidStreamFileException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.Collection;
import java.util.Objects;

/**
 * Utility class for signing stream files
 */
public class StreamFileSigningUtils {
    /**
     * Hidden constructor
     */
    private StreamFileSigningUtils() {}

    /**
     * Supported stream version file
     */
    private static final int SUPPORTED_STREAM_FILE_VERSION = 5;

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
}
