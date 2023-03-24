package com.swirlds.platform.util;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.computeEntireHash;
import static com.swirlds.platform.util.FileSigningUtils.buildSignatureFilePath;
import static com.swirlds.platform.util.FileSigningUtils.signData;

import com.swirlds.common.utility.ByteUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.Collection;
import java.util.Objects;

/**
 * Utility class for signing standard, arbitrary files
 */
public class StandardFileSigningUtils {
    /**
     * Hidden constructor
     */
    private StandardFileSigningUtils() {}

    /**
     * Byte code indicating that the next 4 bytes are a signature length int, followed by a signature of that length
     */
    public static final byte TYPE_SIGNATURE = 3;

    /**
     * Byte code indicating that the next 48 bytes are the SHA-384 hash of the contents of the file to be signed
     */
    public static final byte TYPE_FILE_HASH = 4;

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
     * Accepts a file extension, and returns an all lower-case version of it, without a `.`
     *
     * @param originalExtension the original extension
     * @return the sanitized extension
     */
    @NonNull
    private static String sanitizeExtension(@NonNull final String originalExtension) {
        Objects.requireNonNull(originalExtension, "originalExtension must not be null");

        if (originalExtension.contains(".")) {
            return originalExtension
                    .substring(originalExtension.lastIndexOf(".") + 1)
                    .toLowerCase();
        } else {
            return originalExtension.toLowerCase();
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
                .map(StandardFileSigningUtils::sanitizeExtension)
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
