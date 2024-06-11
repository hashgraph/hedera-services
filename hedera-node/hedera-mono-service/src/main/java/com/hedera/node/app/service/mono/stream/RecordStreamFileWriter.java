/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.stream;

import static com.hedera.node.app.hapi.utils.ByteStringUtils.unwrapUnsafelyIfPossible;
import static com.hedera.node.app.hapi.utils.exports.FileCompressionUtils.COMPRESSION_ALGORITHM_EXTENSION;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.convertInstantToStringWithPadding;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.generateStreamFileNameFromInstant;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.OBJECT_STREAM;
import static com.swirlds.logging.legacy.LogMarker.OBJECT_STREAM_FILE;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Message;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hedera.services.stream.proto.SidecarFile;
import com.hedera.services.stream.proto.SidecarFile.Builder;
import com.hedera.services.stream.proto.SidecarMetadata;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.SignatureFile;
import com.hedera.services.stream.proto.SignatureObject;
import com.hedera.services.stream.proto.SignatureType;
import com.hedera.services.stream.proto.TransactionSidecarRecord.SidecarRecordsCase;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.stream.internal.LinkedObjectStream;
import com.swirlds.logging.legacy.LogMarker;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <i>IMPORTANT:</i> This class does not guarantee that every written record file is a complete
 * 2-second block. That guarantee only holds <b>if the node's JVM has been running and handling
 * transactions since the (consensus) start of the 2-second block</b>.
 *
 * <p>In particular, if a node restarts, and the first record stream {@code item} it gives this
 * class is in a 2-second block {@code B}, but not the first {@code item} in {@code B}; then
 * this class will <b>skip all items received for block {@code B}, and only begin writing items
 * in block {@code B + 1}</b>.
 *
 * <p>Since we only need {@code 1/3} of trustworthy nodes to avoid restarting in the middle of
 * a given block {@code B} to have enough signatures on the resulting record file, this still
 * provides many 9's of availability for the record stream in the absence of a separate
 * catastrophic event.
 *
 * <p>If more than {@code 2/3} of trustworthy nodes <i>did</i> all restart in the middle of a
 * given block (and these were not correlated failures due to a separate catastrophic event
 * that already required event stream recovery); then this exceptional bad luck would require
 * some manual steps to gather signatures on the problem block.
 */
public class RecordStreamFileWriter implements LinkedObjectStream<RecordStreamObject> {
    private static final Logger LOG = LogManager.getLogger(RecordStreamFileWriter.class);

    private static final DigestType currentDigestType = Cryptography.DEFAULT_DIGEST_TYPE;

    /**
     * The current record stream type; used to obtain file extensions and versioning
     */
    private final RecordStreamType streamType;

    /**
     * A messageDigest object for digesting entire stream file and generating entire record stream
     * file hash
     */
    private final MessageDigest streamDigest;

    /**
     * A messageDigest object for digesting metaData in the stream file and generating metaData
     * hash. Metadata contains: record stream version || HAPI proto version || startRunningHash ||
     * endRunningHash || blockNumber, where || denotes concatenation
     */
    private final MessageDigest metadataStreamDigest;

    /**
     * A messageDigest object for digesting sidecar files and generating sidecar file hash
     */
    private final MessageDigest sidecarStreamDigest;

    /**
     * Current runningHash before consuming the object added by calling {@link
     * #addObject(RecordStreamObject)} method
     */
    private RunningHash runningHash;

    /**
     * Signer for generating signatures
     */
    private final Signer signer;

    /**
     * The id that the next sidecar file of the current period will have. Initialized to 1 at the
     * start of each period. Incremented by 1 with each new sidecar file.
     */
    private int sidecarFileId;

    /**
     * Used to keep track of the size that the current {@code sidecarFileBuilder} will be on disk
     * and externalizing it before it goes over {@code maxSidecarFileSize}
     */
    private int currentSidecarFileSize;

    /**
     * The max file size (in bytes) a sidecar file can have
     */
    private final int maxSidecarFileSize;

    /**
     * The instant of the first transaction in the current period
     */
    private Instant firstTxnInstant;

    /**
     * The path to which we write record stream files and signature files
     */
    private final String dirPath;

    /**
     * The path to which we write sidecar record stream files
     */
    private final String sidecarDirPath;

    /**
     * Whether we should overwrite an existing record file on disk, because we are doing recovery.
     * (If false, this class just skips any record files that already exist.)
     */
    private final boolean isEventStreamRecovery;

    /**
     * The functional that will be used to try to delete a file; we only have this as a field so
     * pass in a failing deletion functional for unit tests.
     */
    private final Predicate<File> tryDeletion;

    private int recordFileVersion;
    private RecordStreamFile.Builder recordStreamFileBuilder;
    private SidecarFile.Builder sidecarFileBuilder;
    private final EnumSet<SidecarType> sidecarTypesInCurrentSidecar;
    private final GlobalDynamicProperties dynamicProperties;

    public RecordStreamFileWriter(
            final String dirPath,
            final Signer signer,
            final RecordStreamType streamType,
            final String sidecarDirPath,
            final int maxSidecarFileSize,
            final boolean isEventStreamRecovery,
            @NonNull final Predicate<File> tryDeletion,
            final GlobalDynamicProperties globalDynamicProperties)
            throws NoSuchAlgorithmException {
        this.dirPath = dirPath;
        this.signer = signer;
        this.streamType = streamType;
        this.isEventStreamRecovery = isEventStreamRecovery;
        this.tryDeletion = tryDeletion;
        this.streamDigest = MessageDigest.getInstance(currentDigestType.algorithmName());
        this.metadataStreamDigest = MessageDigest.getInstance(currentDigestType.algorithmName());
        this.sidecarStreamDigest = MessageDigest.getInstance(currentDigestType.algorithmName());
        this.sidecarDirPath = sidecarDirPath;
        this.sidecarTypesInCurrentSidecar = EnumSet.noneOf(SidecarType.class);
        this.sidecarFileId = 1;
        this.maxSidecarFileSize = maxSidecarFileSize;
        this.dynamicProperties = globalDynamicProperties;
    }

    @Override
    public void addObject(final RecordStreamObject object) {
        if (object.closesCurrentFile()) {
            // if we are currently writing a file,
            // finish current file and generate signature file
            closeCurrentAndSign();
            // write the beginning of the new file
            beginNew(object);
        }

        if (recordStreamFileBuilder != null) {
            consume(object);
        }

        // update runningHash
        this.runningHash = object.getRunningHash();
    }

    /**
     * if recordStreamFile is not null: write last runningHash to current file; close current file;
     * and generate a corresponding signature file
     */
    public void closeCurrentAndSign() {
        if (recordStreamFileBuilder != null) {
            // generate record file name
            assertFirstTxnInstantIsKnown();
            final var uncompressedRecordFilePath = generateRecordFilePath(firstTxnInstant);
            final var recordFile = new File(
                    dynamicProperties.shouldCompressRecordFilesOnCreation()
                            ? uncompressedRecordFilePath + COMPRESSION_ALGORITHM_EXTENSION
                            : uncompressedRecordFilePath);
            final var recordFileNameShort = recordFile.getName(); // for logging purposes
            final var fileExists = recordFile.exists() && !recordFile.isDirectory();
            if (fileExists && !isEventStreamRecovery) {
                LOG.debug(OBJECT_STREAM.getMarker(), "Stream file already exists {}", recordFileNameShort);
            } else {
                if (fileExists && !tryDeletion.test(recordFile)) {
                    throw new IllegalStateException("Could not delete existing record file '" + recordFileNameShort
                            + "' to replace during recovery, aborting");
                }
                try {
                    // write endRunningHash
                    final var endRunningHash = runningHash.getFutureHash().get();
                    recordStreamFileBuilder.setEndObjectRunningHash(toProto(endRunningHash.copyToByteArray()));
                    LOG.debug(
                            OBJECT_STREAM_FILE.getMarker(),
                            "closeCurrentAndSign :: write endRunningHash {}",
                            endRunningHash);
                } catch (final InterruptedException | ExecutionException e) {
                    Thread.currentThread().interrupt();
                    LOG.error(
                            EXCEPTION.getMarker(),
                            "closeCurrentAndSign :: failed when getting endRunningHash for writing" + " {}",
                            recordFileNameShort,
                            e);
                    return;
                }

                // create sidecar file
                if (sidecarFileBuilder.getSidecarRecordsCount() > 0) {
                    try {
                        finalizeCurrentSidecar();
                    } catch (final IOException e) {
                        Thread.currentThread().interrupt();
                        LOG.warn(
                                EXCEPTION.getMarker(),
                                "closeCurrentAndSign :: {} when creating sidecar files",
                                e.getClass().getSimpleName(),
                                e);
                        return;
                    }
                }

                streamDigest.reset();
                final RecordStreamFile protoRecordFile;
                try (final FileOutputStream stream = new FileOutputStream(recordFile, false);
                        final GZIPOutputStream gzipStream = dynamicProperties.shouldCompressRecordFilesOnCreation()
                                ? new GZIPOutputStream(stream)
                                : null;
                        final SerializableDataOutputStream dos =
                                new SerializableDataOutputStream(new BufferedOutputStream(new HashingOutputStream(
                                        streamDigest, gzipStream != null ? gzipStream : stream)))) {
                    LOG.debug(OBJECT_STREAM_FILE.getMarker(), "Stream file created {}", recordFileNameShort);

                    // write contents of record file - record file version and serialized RecordFile
                    // protobuf
                    dos.writeInt(recordFileVersion);
                    protoRecordFile = recordStreamFileBuilder.build();
                    dos.write(serialize(protoRecordFile));

                    // make sure the whole file is written to disk
                    dos.flush();
                    if (gzipStream != null) {
                        // GZIPOutputStream takes care of flushing its wrapped stream
                        gzipStream.flush();
                    } else {
                        stream.flush();
                    }
                    stream.getChannel().force(true);
                    stream.getFD().sync();
                    LOG.debug(
                            OBJECT_STREAM_FILE.getMarker(), "Stream file written successfully {}", recordFileNameShort);

                    // close dosMeta manually; stream and dos will be automatically closed
                    recordStreamFileBuilder = null;

                    LOG.debug(
                            OBJECT_STREAM_FILE.getMarker(),
                            "File {} is closed at {}",
                            () -> recordFileNameShort,
                            Instant::now);
                } catch (final IOException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn(
                            EXCEPTION.getMarker(),
                            "closeCurrentAndSign :: IOException when serializing {}",
                            recordStreamFileBuilder,
                            e);
                    return;
                }

                // if this line is reached, record file has been created successfully, so create its
                // signature
                try {
                    createSignatureFileFor(protoRecordFile, uncompressedRecordFilePath);
                } catch (UnsupportedOperationException e) {
                    // Normally we don't need to be able to sign record files during
                    // event stream recovery since the most common use case is ad-hoc
                    // investigation of node behavior; but for any other init trigger, it
                    // is critical to surface this exception
                    if (!isEventStreamRecovery) {
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * Prepares internal state to start writing a new record file. The one really critical piece
     * of information we must track here in the {@code recordStreamFileBuilder} is the current
     * running hash, since that will be lost by the time we {@link #closeCurrentAndSign()}.
     *
     * <p>Note we don't initialize any part of the metadata hash digest here, because we'll have
     * all the required information we need when we {@link #closeCurrentAndSign()}; and
     * computing the entire digest there makes it easier to verify correctness. (C.f.
     * https://github.com/hashgraph/hedera-services/issues/8738)
     */
    private void beginNew(final RecordStreamObject object) {
        final var fileHeader = streamType.getFileHeader();
        // instead of creating the record file here and writing
        // the record file version in it, save the version and
        // perform the whole file creation in {@link #closeCurrentAndSign()} method
        recordFileVersion = fileHeader[0];
        // reset fields
        firstTxnInstant = null;
        resetSidecarFields();
        sidecarFileId = 1;
        recordStreamFileBuilder = RecordStreamFile.newBuilder().setBlockNumber(object.getStreamAlignment());
        recordStreamFileBuilder.setHapiProtoVersion(SemanticVersion.newBuilder()
                .setMajor(fileHeader[1])
                .setMinor(fileHeader[2])
                .setPatch(fileHeader[3]));
        try {
            final var startRunningHash = runningHash.getFutureHash().get();
            recordStreamFileBuilder.setStartObjectRunningHash(toProto(startRunningHash.copyToByteArray()));
            LOG.debug(
                    OBJECT_STREAM_FILE.getMarker(),
                    "beginNew :: write startRunningHash to metadata {}",
                    startRunningHash);
        } catch (final InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            LOG.error(
                    EXCEPTION.getMarker(),
                    "beginNew :: Exception when getting startRunningHash for writing to metadata" + " stream",
                    e);
        }
    }

    /**
     * add given object to the current record stream file
     *
     * @param object object to be added to the record stream file
     */
    private void consume(final RecordStreamObject object) {
        recordStreamFileBuilder.addRecordStreamItems(RecordStreamItem.newBuilder()
                .setTransaction(object.getTransaction())
                .setRecord(object.getTransactionRecord())
                .build());

        final var sidecars = object.getSidecars();
        if (!sidecars.isEmpty()) {
            for (final var sidecarBuilder : sidecars) {
                // build() and getSerializedSize() would have been called anyway by the proto
                // library downstream, so we do not incur any performance losses calling them here
                // getSerializedSize() caches its result internally
                final var sidecar = sidecarBuilder.build();
                final var sidecarSizeInBytes = sidecar.getSerializedSize();
                if (currentSidecarFileSize + sidecarSizeInBytes >= maxSidecarFileSize) {
                    assertFirstTxnInstantIsKnown();
                    try {
                        finalizeCurrentSidecar();
                    } catch (final IOException e) {
                        Thread.currentThread().interrupt();
                        LOG.warn(
                                EXCEPTION.getMarker(),
                                "consume :: {} when creating sidecar files",
                                e.getClass().getSimpleName(),
                                e);
                        return;
                    }
                    resetSidecarFields();
                    sidecarFileId++;
                }
                if (sidecar.getSidecarRecordsCase() != SidecarRecordsCase.SIDECARRECORDS_NOT_SET) {
                    if (sidecar.getSidecarRecordsCase() == SidecarRecordsCase.STATE_CHANGES) {
                        sidecarTypesInCurrentSidecar.add(SidecarType.CONTRACT_STATE_CHANGE);
                    } else if (sidecar.getSidecarRecordsCase() == SidecarRecordsCase.ACTIONS) {
                        sidecarTypesInCurrentSidecar.add(SidecarType.CONTRACT_ACTION);
                    } else if (sidecar.getSidecarRecordsCase() == SidecarRecordsCase.BYTECODE) {
                        sidecarTypesInCurrentSidecar.add(SidecarType.CONTRACT_BYTECODE);
                    }
                    currentSidecarFileSize += sidecarSizeInBytes;
                    sidecarFileBuilder.addSidecarRecords(sidecar);
                } else { // TODO this error should be the same
                    LOG.warn("A sidecar record without an actual sidecar has been received");
                }
            }
        }
    }

    /**
     * generate full record file path from given Instant object
     *
     * @param consensusTimestamp the consensus timestamp of the first transaction in the record file
     * @return the new record file path
     */
    String generateRecordFilePath(final Instant consensusTimestamp) {
        return dirPath + File.separator + generateStreamFileNameFromInstant(consensusTimestamp, streamType);
    }

    /**
     * generate full sidecar file path from given Instant object
     *
     * @param consensusTimestamp the consensus timestamp of the first transaction in the record file
     *                           this sidecar file is associated with
     * @param sidecarId          the sidecar id of this sidecar file
     * @return the new sidecar file path
     */
    String generateSidecarFilePath(final Instant consensusTimestamp, final int sidecarId) {
        var sidecarPath = sidecarDirPath
                + File.separator
                + convertInstantToStringWithPadding(consensusTimestamp)
                + "_"
                + String.format("%02d", sidecarId)
                + "."
                + streamType.getSidecarExtension();
        if (dynamicProperties.shouldCompressRecordFilesOnCreation()) {
            sidecarPath += COMPRESSION_ALGORITHM_EXTENSION;
        }
        return sidecarPath;
    }

    public void setRunningHash(final Hash hash) {
        this.runningHash = new RunningHash(hash);
    }

    /**
     * this method is called when the node falls behind resets all populated up to this point fields
     * (metadataDigest, dosMeta, recordStreamFile)
     */
    @Override
    public void clear() {
        recordStreamFileBuilder = null;
        LOG.debug(OBJECT_STREAM.getMarker(), "RecordStreamFileWriter::clear executed.");
    }

    public void close() {
        this.closeCurrentAndSign();
        LOG.debug(LogMarker.FREEZE.getMarker(), "RecordStreamFileWriter finished writing the last object, is stopped");
    }

    private void assertFirstTxnInstantIsKnown() {
        if (firstTxnInstant == null) {
            final var firstTxnTimestamp =
                    recordStreamFileBuilder.getRecordStreamItems(0).getRecord().getConsensusTimestamp();
            firstTxnInstant = Instant.ofEpochSecond(firstTxnTimestamp.getSeconds(), firstTxnTimestamp.getNanos());
        }
    }

    /**
     * Helper method that serializes an arbitrary Message.Builder. Uses deterministic serialization
     * to ensure multiple invocations on the same object lead to identical serialization.
     *
     * @param message the object that needs to be serialized
     * @return the serialized bytes
     */
    private byte[] serialize(final Message message) throws IOException {
        final var result = new byte[message.getSerializedSize()];
        final var output = CodedOutputStream.newInstance(result);
        output.useDeterministicSerialization();
        message.writeTo(output);
        output.checkNoSpaceLeft();
        return result;
    }

    private HashObject toProto(final byte[] hash) {
        return HashObject.newBuilder()
                .setAlgorithm(HashAlgorithm.SHA_384)
                .setLength(currentDigestType.digestLength())
                .setHash(ByteStringUtils.wrapUnsafely(hash))
                .build();
    }

    private void createSignatureFileFor(final RecordStreamFile protoRecordFile, final String relatedRecordStreamFile) {
        // First compute the signature for the record stream file based on the data written to the
        // record file's HashingOutputStream; note all of this data in closeCurrentAndSign()
        final var fileSignature = generateSignatureObject(streamDigest.digest());

        metadataStreamDigest.reset();
        // Next compute the signature for the metadata file based on the given record file's contents
        try (final var out = new SerializableDataOutputStream(new HashingOutputStream(metadataStreamDigest))) {
            for (final var versionPart : streamType.getFileHeader()) {
                out.writeInt(versionPart);
            }
            out.write(unwrapUnsafelyIfPossible(
                    protoRecordFile.getStartObjectRunningHash().getHash()));
            out.write(unwrapUnsafelyIfPossible(
                    protoRecordFile.getEndObjectRunningHash().getHash()));
            out.writeLong(protoRecordFile.getBlockNumber());
        } catch (IOException e) {
            // Should never get here except in some truly dire circumstances
            LOG.error(
                    EXCEPTION.getMarker(),
                    "Failed to write metadata digest contents for {}, skipping its signature file",
                    relatedRecordStreamFile,
                    e);
            return;
        }
        final var metadataSignature = generateSignatureObject(metadataStreamDigest.digest());

        // Finally, construct and write the signature file
        final var signatureFile =
                SignatureFile.newBuilder().setFileSignature(fileSignature).setMetadataSignature(metadataSignature);
        final var sigFilePath = relatedRecordStreamFile + "_sig";
        try (final var fos = new FileOutputStream(sigFilePath)) {
            // version in signature files is 1 byte, compared to 4 in record files
            fos.write(streamType.getSigFileHeader()[0]);
            fos.write(serialize(signatureFile.build()));
            LOG.debug(OBJECT_STREAM_FILE.getMarker(), "closeCurrentAndSign :: signature file saved: {}", sigFilePath);
        } catch (final IOException e) {
            LOG.error(
                    EXCEPTION.getMarker(),
                    "closeCurrentAndSign ::  :: Fail to generate signature file for {}",
                    relatedRecordStreamFile,
                    e);
        }
    }

    private SignatureObject generateSignatureObject(final byte[] hash) {
        final var signature = signer.sign(hash).getSignatureBytes();
        return SignatureObject.newBuilder()
                .setType(SignatureType.SHA_384_WITH_RSA)
                .setLength(signature.length)
                .setChecksum(101 - signature.length) // simple checksum to detect if at wrong place in
                // the stream
                .setSignature(ByteStringUtils.wrapUnsafely(signature))
                .setHashObject(toProto(hash))
                .build();
    }

    private void createSidecarFile(final Builder sidecarFileBuilder, final File sidecarFile) throws IOException {
        try (final FileOutputStream stream = new FileOutputStream(sidecarFile, false);
                final GZIPOutputStream gzipStream =
                        dynamicProperties.shouldCompressRecordFilesOnCreation() ? new GZIPOutputStream(stream) : null;
                final SerializableDataOutputStream dos = new SerializableDataOutputStream(new BufferedOutputStream(
                        new HashingOutputStream(sidecarStreamDigest, gzipStream != null ? gzipStream : stream)))) {
            // write contents of sidecar
            final var protoSidecarFile = sidecarFileBuilder.build();
            dos.write(serialize(protoSidecarFile));

            // make sure the whole sidecar is written to disk before continuing
            // with calculating its hash and saving it as part of the SidecarMetadata
            dos.flush();
            if (gzipStream != null) {
                // GZIPOutputStream takes care of flushing its wrapped stream
                gzipStream.flush();
            } else {
                stream.flush();
            }
            stream.getChannel().force(true);
            stream.getFD().sync();

            LOG.debug(OBJECT_STREAM_FILE.getMarker(), "Sidecar file created successfully {}", sidecarFile.getName());
        }
    }

    private SidecarMetadata.Builder createSidecarMetadata() {
        return SidecarMetadata.newBuilder()
                .setHash(toProto(sidecarStreamDigest.digest()))
                .setId(sidecarFileId)
                .addAllTypes(sidecarTypesInCurrentSidecar);
    }

    private void finalizeCurrentSidecar() throws IOException {
        final var sidecarFile = new File(generateSidecarFilePath(firstTxnInstant, sidecarFileId));
        createSidecarFile(sidecarFileBuilder, sidecarFile);
        recordStreamFileBuilder.addSidecars(createSidecarMetadata());
    }

    private void resetSidecarFields() {
        sidecarFileBuilder = SidecarFile.newBuilder();
        sidecarTypesInCurrentSidecar.clear();
        currentSidecarFileSize = 0;
    }

    public int getMaxSidecarFileSize() {
        return this.maxSidecarFileSize;
    }

    @VisibleForTesting
    void clearRunningHash() {
        runningHash = new RunningHash();
    }
}
