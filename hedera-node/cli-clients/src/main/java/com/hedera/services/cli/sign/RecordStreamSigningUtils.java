/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.sign;

import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.readMaybeCompressedRecordStreamFile;
import static com.hedera.services.stream.proto.SignatureType.SHA_384_WITH_RSA;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.readFirstIntFromFile;
import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.platform.util.FileSigningUtils.signData;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import com.hedera.node.app.hapi.utils.exports.FileCompressionUtils;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SignatureFile;
import com.hedera.services.stream.proto.SignatureObject;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Utility class for signing record stream files
 */
@SuppressWarnings("java:S106") // Suppressing the usage of System.out.println instead of a logger
public class RecordStreamSigningUtils {

    /**
     * Hidden constructor
     */
    private RecordStreamSigningUtils() {}

    /**
     * Supported stream version file
     */
    public static final int SUPPORTED_STREAM_FILE_VERSION = 6;

    /**
     * a messageDigest object for digesting entire stream file and generating entire record stream
     * file hash
     */
    private static MessageDigest streamDigest;

    /**
     * a messageDigest object for digesting metaData in the stream file and generating metaData
     * hash. Metadata contains: record stream version || HAPI proto version || startRunningHash ||
     * endRunningHash || blockNumber, where || denotes concatenation
     */
    private static MessageDigest metadataStreamDigest;

    /**
     * Generates a signature file for the given record stream file
     *
     * @param signatureFileDestination the full path where the signature file will be generated
     * @param streamFileToSign         the stream file to be signed
     * @param keyPair                  the keyPair used for signing
     * @param hapiVersion              the hapi protobuf version
     * @return true if the signature file was generated successfully, false otherwise
     */
    @SuppressWarnings("java:S112") // Suppressing that we are throwing RuntimeException(generic exception)
    public static boolean signRecordStreamFile(
            @NonNull final Path signatureFileDestination,
            @NonNull final Path streamFileToSign,
            @NonNull final KeyPair keyPair,
            @NonNull final String hapiVersion) {

        Objects.requireNonNull(signatureFileDestination, "signatureFileDestination must not be null");
        Objects.requireNonNull(streamFileToSign, "streamFileToSign must not be null");
        Objects.requireNonNull(keyPair, "keyPair must not be null");
        Objects.requireNonNull(hapiVersion, "hapiVersion must not be null");

        try {
            final var recordFile = streamFileToSign.toFile();
            final int version = getRecordStreamVersion(recordFile);
            if (version != SUPPORTED_STREAM_FILE_VERSION) {
                System.err.printf(
                        "signRecordStreamFile :: Failed to sign file [%s] with unsupported version [%s]%n",
                        streamFileToSign.getFileName(), version);
                return false;
            }

            initRecordDigest();
            int[] fileHeader = createFileHeader(hapiVersion);

            outputStreamDigest(fileHeader, recordFile.getAbsolutePath());
            SignatureFile.Builder signatureFile = createSignatureFile(keyPair);
            generateSigRecordStreamFile(signatureFileDestination.toFile(), signatureFile);

            System.out.println("Generated signature file: " + signatureFileDestination);

            return true;
        } catch (final SignatureException | InvalidKeyException | InvalidProtobufVersionException | IOException e) {
            System.err.printf(
                    "signRecordStreamFile :: Failed to sign file [%s] with exception : [%s]%n", streamFileToSign, e);
            return false;
        } catch (final NoSuchAlgorithmException | NoSuchProviderException e) {
            System.err.printf(
                    "signRecordStreamFile :: Irrecoverable error encountered when signing [%s] with exception : [%s]%n",
                    streamFileToSign, e);
            throw new RuntimeException("Irrecoverable error encountered", e);
        }
    }

    private static int getRecordStreamVersion(File recordFile) throws IOException {
        try {
            final var isCompressed = RecordStreamType.getInstance().isGzFile(recordFile.getName());
            if (isCompressed) {
                final var uncompressedFileContents =
                        FileCompressionUtils.readUncompressedFileBytes(recordFile.getAbsolutePath());
                return ByteBuffer.wrap(uncompressedFileContents, 0, 4).getInt();
            } else {
                return readFirstIntFromFile(recordFile);
            }

        } catch (final IOException e) {
            System.err.printf(
                    "getRecordStreamVersion :: Failed to read record stream version from file [%s] with exception : [%s]%n",
                    recordFile.getAbsolutePath(), e);
            throw e;
        }
    }

    private static int[] createFileHeader(@NonNull final String hapiVersion) throws InvalidProtobufVersionException {
        Objects.requireNonNull(hapiVersion, "hapiVersion must not be null");

        final String[] versions = hapiVersion
                .replace("-SNAPSHOT", "")
                .split(Pattern.quote("-"))[0]
                .split(Pattern.quote("."));

        if (versions.length >= 3) {
            try {
                return new int[] {
                    SUPPORTED_STREAM_FILE_VERSION,
                    Integer.parseInt(versions[0]),
                    Integer.parseInt(versions[1]),
                    Integer.parseInt(versions[2]),
                };
            } catch (final NumberFormatException e) {
                System.err.printf(
                        "createFileHeader :: Error when parsing protobuf version string  [%s] with exception : [%s]%n",
                        hapiVersion, e);
                throw new InvalidProtobufVersionException("Invalid hapi version string: " + hapiVersion);
            }
        } else {
            System.err.printf("createFileHeader :: Error when parsing protobuf version string [%s]%n", hapiVersion);
            throw new InvalidProtobufVersionException("Invalid hapi version string: " + hapiVersion);
        }
    }

    private static SignatureFile.Builder createSignatureFile(final KeyPair sigKeyPair)
            throws SignatureException, InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException {
        final SignatureObject metadataSignature = generateSignatureObject(metadataStreamDigest.digest(), sigKeyPair);
        final SignatureObject fileSignature = generateSignatureObject(streamDigest.digest(), sigKeyPair);
        return SignatureFile.newBuilder().setFileSignature(fileSignature).setMetadataSignature(metadataSignature);
    }

    private static void generateSigRecordStreamFile(final File filePath, SignatureFile.Builder signatureFile)
            throws IOException {
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(filePath))) {
            output.write(RecordStreamType.getInstance().getSigFileHeader()[0]);
            signatureFile.build().writeTo(output);
        } catch (final IOException e) {
            System.err.printf(
                    "generateSigRecordStreamFile :: Failed to generate signature file for [%s] with exception : [%s]%n",
                    filePath.getAbsolutePath(), e);
            throw e;
        }
    }

    // Suppressing the warning that in log.info we shouldn't pass invoking method
    @SuppressWarnings("java:S2629")
    private static void outputStreamDigest(int[] fileHeader, String recordFile) throws IOException {
        try (final SerializableDataOutputStream dosMeta =
                        new SerializableDataOutputStream(new HashingOutputStream(metadataStreamDigest));
                final SerializableDataOutputStream dos = new SerializableDataOutputStream(
                        new BufferedOutputStream(new HashingOutputStream(streamDigest)))) {

            // parse record file
            final Pair<Integer, Optional<RecordStreamFile>> recordResult =
                    readMaybeCompressedRecordStreamFile(recordFile);
            final var f = recordResult.getValue();
            if (f.isPresent()) {
                if (f.get().hasStartObjectRunningHash()) {
                    final long blockNumber = f.get().getBlockNumber();
                    final byte[] startRunningHash =
                            f.get().getStartObjectRunningHash().getHash().toByteArray();
                    final byte[] endRunningHash =
                            f.get().getEndObjectRunningHash().getHash().toByteArray();
                    final int version = recordResult.getKey();
                    final byte[] serializedBytes = f.get().toByteArray();

                    // update stream digest
                    System.out.printf("Writing file header [%s]%n", Arrays.toString(fileHeader));
                    for (final int value : fileHeader) {
                        dosMeta.writeInt(value);
                    }
                    System.out.printf("Writing start running hash [%s]%n", hex(startRunningHash));
                    dosMeta.write(startRunningHash);
                    System.out.printf("Writing end running hash [%s]%n", hex(endRunningHash));
                    dosMeta.write(endRunningHash);
                    System.out.printf("Writing block number [%s]%n", blockNumber);
                    dosMeta.writeLong(blockNumber);
                    dosMeta.flush();

                    // update meta digest
                    System.out.printf("Writing version [%s]%n", version);
                    dos.writeInt(version);
                    System.out.printf(
                            "Writing serializedBytes [%s]%n",
                            hex(serializedBytes).substring(0, 32));
                    dos.write(serializedBytes);
                    dos.flush();
                } else {
                    System.err.printf(
                            "outputStreamDigest :: Failed to parse record file [%s] with exception : [%s]%n",
                            recordFile, "RecordStreamFile does not have startObjectRunningHash");

                    throw new IOException("RecordStreamFile does not have startObjectRunningHash");
                }
            } else {
                System.err.printf(
                        "outputStreamDigest :: Failed to parse record file [%s] with exception : [%s]%n",
                        recordFile, "RecordStreamFile is empty");
                throw new IOException("RecordStreamFile is empty");
            }

        } catch (final IOException e) {
            System.err.printf(
                    "outputStreamDigest :: Got IOException when output steam digest [%s] with exception : [%s]%n",
                    recordFile, e);
            throw e;
        }
    }

    private static void initRecordDigest() throws NoSuchAlgorithmException {
        try {
            streamDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
            metadataStreamDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
        } catch (final NoSuchAlgorithmException e) {
            System.err.printf("initRecordDigest :: Failed to get message digest with exception : [%s]%n", e);
            throw e;
        }
    }

    private static SignatureObject generateSignatureObject(final byte[] hash, final KeyPair sigKeyPair)
            throws NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidKeyException {
        final byte[] signature = signData(hash, sigKeyPair);
        return SignatureObject.newBuilder()
                .setType(SHA_384_WITH_RSA)
                .setLength(signature.length)
                .setChecksum(101 - signature.length) // simple checksum to detect if at wrong place in
                // the stream
                .setSignature(wrapUnsafely(signature))
                .setHashObject(toProto(hash))
                .build();
    }

    private static ByteString wrapUnsafely(@NonNull final byte[] bytes) {
        return UnsafeByteOperations.unsafeWrap(bytes);
    }

    private static HashObject toProto(final byte[] hash) {
        return HashObject.newBuilder()
                .setAlgorithm(HashAlgorithm.SHA_384)
                .setLength(DigestType.SHA_384.digestLength())
                .setHash(wrapUnsafely(hash))
                .build();
    }
}
