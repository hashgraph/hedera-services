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

package com.hedera.node.app.records.files;

import static com.hedera.node.app.records.files.SignatureFileWriter.writeSignatureFile;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.convertInstantToStringWithPadding;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.streams.HashAlgorithm;
import com.hedera.hapi.streams.HashObject;
import com.hedera.hapi.streams.SidecarMetadata;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.node.app.records.BlockRecordStreamConfig;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.stream.Signer;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Base class for StreamFileProducers that produce record and sidecar streams.
 * <p>
 *     Threading model: is really important here, This class depends on the fact that public methods are all called on
 *     handle transaction thread. So are single threaded and executed in the correct order.
 * </p>
 * <p>
 *     This base class has no mutable state, all methods should be stateless so they can be called from any thread in subclasses.
 * </p>
 */
@SuppressWarnings("SwitchStatementWithTooFewBranches")
public abstract class StreamFileProducerBase implements AutoCloseable {
    protected static final Logger log = LogManager.getLogger(StreamFileProducerBase.class);
    /** The file extension for record files */
    public static final String RECORD_EXTENSION = "rcd";
    /** The suffix added to RECORD_EXTENSION when they are compressed */
    public static final String COMPRESSION_ALGORITHM_EXTENSION = ".gz";

    /** signer for generating signatures */
    private final Signer signer;
    /** The HAPI protobuf version */
    protected final SemanticVersion hapiVersion;
    /** The version of the record file format */
    protected final int recordFileVersion;
    /** The version of the signature file format */
    private final int signatureFileVersion;
    /** The maximum size of a sidecar file in bytes */
    private final int maxSideCarSizeInBytes;
    /** The directory where record files are written */
    protected final Path nodeScopedRecordLogDir;
    /** The directory where sidecar files are written */
    private final Path nodeScopedSidecarDir;
    /** True if record files should be compressed on creation */
    protected final boolean compressFilesOnCreation;
    /** The format for creating the record files */
    protected final RecordFileFormat recordFileFormat;

    /**
     * Construct RecordManager and start background thread
     *
     * @param configProvider the configuration to read from
     * @param nodeInfo the current node information
     * @param signer the signer to use for signing in signature files
     * @param fileSystem the file system to use, needed for testing to be able to use a non-standard file
     *                   system. If null default is used.
     */
    public StreamFileProducerBase(
            @NonNull final ConfigProvider configProvider,
            @NonNull final NodeInfo nodeInfo,
            @NonNull final Signer signer,
            @Nullable final FileSystem fileSystem) {
        this.signer = signer;
        // read configuration
        BlockRecordStreamConfig recordStreamConfig =
                configProvider.getConfiguration().getConfigData(BlockRecordStreamConfig.class);
        recordFileVersion = recordStreamConfig.recordFileVersion();
        signatureFileVersion = recordStreamConfig.signatureFileVersion();
        compressFilesOnCreation = recordStreamConfig.compressFilesOnCreation();
        maxSideCarSizeInBytes = recordStreamConfig.sidecarMaxSizeMb() * 1024 * 1024;
        // get hapi version
        hapiVersion = nodeInfo.hapiVersion();
        // compute directories for record and sidecar files
        final Path logDir = fileSystem != null
                ? fileSystem.getPath(recordStreamConfig.logDir())
                : Path.of(recordStreamConfig.logDir());
        nodeScopedRecordLogDir = logDir.resolve("record" + nodeInfo.accountMemo());
        nodeScopedSidecarDir = nodeScopedRecordLogDir.resolve(recordStreamConfig.sidecarDir());
        // pick record file format
        recordFileFormat = switch (recordFileVersion) {
            case 6 -> RecordFileFormatV6.INSTANCE;
            default -> throw new IllegalArgumentException("Unknown record file version: " + recordFileVersion);};
    }

    // =========================================================================================================================================================================
    // public abstract methods

    /**
     * Set the current running hash of record stream items. This is called only once to initialize the running hash on startup.
     * This is called on handle transaction thread.
     *
     * @param recordStreamItemRunningHash The new running hash, all future running hashes produced will flow from this
     */
    public abstract void setRunningHash(Bytes recordStreamItemRunningHash);

    /**
     * Get the current running hash of record stream items. This is called on handle transaction thread. It will block
     * if background thread is still hashing. It will always return the running hash after the last user transaction
     * was added. Hence, any pre-transactions or others not yet committed via
     * {@link StreamFileProducerBase#writeRecordStreamItems(long, Instant, Stream)} will not be included.
     *
     * @return The current running hash upto and including the last record stream item sent in writeRecordStreamItems().
     */
    @Nullable
    public abstract Bytes getRunningHash();

    /**
     * Get the previous, previous, previous runningHash of all RecordStreamObject. This will block if
     * the running hash has not yet been computed for the most recent user transaction.
     *
     * @return the previous, previous, previous runningHash of all RecordStreamObject
     */
    @Nullable
    public abstract Bytes getNMinus3RunningHash();

    /**
     * Called at the end of a block and start of next block.
     * <p>If there is a currently open block it closes any open stream files and writes signature file.</p>
     * <p>Then opens a new record file for the new block</p>
     *
     * @param lastBlockNumber                       The number for the block we are closing. If lastBlockNumber is &lt;=0 then
     *                                              there is no block to close.
     * @param newBlockNumber                        The number for the block we are opening.
     * @param newBlockFirstTransactionConsensusTime The consensus time of the first transaction in the new block. It must be the
     *                                              adjusted consensus time not the platform assigned consensus time. Assuming
     *                                              the two are different.
     */
    public abstract void switchBlocks(
            long lastBlockNumber, long newBlockNumber, Instant newBlockFirstTransactionConsensusTime);

    /**
     * Write record items to stream files. They must be in exact consensus time order! This must only be called after the user
     * transaction has been committed to state and is 100% done. So is called exactly once per user transaction at the end
     * after it has been committed to state. Each call is for a complete set of transactions that represent a single user
     * transaction and its pre-transactions and child-transactions.
     *
     * @param blockNumber the block number for this block that we are writing record stream items for
     * @param blockFirstTransactionConsensusTime the consensus time of the first transaction in the block
     * @param recordStreamItems the record stream items to write
     */
    public abstract void writeRecordStreamItems(
            final long blockNumber,
            @NonNull Instant blockFirstTransactionConsensusTime,
            @NonNull final Stream<SingleTransactionRecord> recordStreamItems);

    /**
     * Closes this StreamFileProducerBase wait for any background thread, close all files etc.
     */
    @Override
    public abstract void close() throws Exception;

    // =========================================================================================================================================================================
    // common protected methods used by subclasses

    /**
     * Close the current block, this:
     * <ul>
     *     <li>closes the most recent sidecar file</li>
     *     <li>finishes writing the current record file, including sidecar metadata</li>
     *     <li>create record file and metadata hashes</li>
     *     <li>signs the hashes and write signature file</li>
     * </ul>
     *
     * @param startingRunningHash     the starting running hash before thr first record stream item of this block
     * @param finalRunningHash        the final running hash of record stream items at the end of the block
     * @param currentRecordFileWriter the current open record file writer
     * @param sidecarFileWriters      list of sidecar file writers for this block, null if there are none
     */
    protected final void closeBlock(
            @NonNull final Bytes startingRunningHash,
            @NonNull final Bytes finalRunningHash,
            @NonNull final RecordFileWriter currentRecordFileWriter,
            @Nullable final List<SidecarFileWriter> sidecarFileWriters) {
        final long blockNumber = currentRecordFileWriter.blockNumber();
        try {
            // close any open sidecar writers and add sidecar metadata to record file
            final List<SidecarMetadata> sidecarMetadata = new ArrayList<>();
            if (sidecarFileWriters != null) {
                for (int i = 0; i < sidecarFileWriters.size(); i++) {
                    final SidecarFileWriter sidecarFileWriter = sidecarFileWriters.get(i);
                    // close it in case it is not closed already
                    sidecarFileWriter.close();
                    // get the sidecar hash
                    final Bytes sidecarHash = sidecarFileWriter.fileHash();
                    // create and add sidecar metadata to record file
                    sidecarMetadata.add(new SidecarMetadata(
                            new HashObject(HashAlgorithm.SHA_384, (int) sidecarHash.length(), sidecarHash),
                            i + 1, // sidecar file indexes start at 1
                            sidecarFileWriter.types()));
                }
            }
            // write the stream footer
            currentRecordFileWriter.writeFooter(
                    new HashObject(HashAlgorithm.SHA_384, (int) finalRunningHash.length(), finalRunningHash),
                    sidecarMetadata);
            currentRecordFileWriter.close();
            // write signature file, this tells the uploader that this record file set is complete
            writeSignatureFile(
                    currentRecordFileWriter.filePath(),
                    currentRecordFileWriter.uncompressedFileHash(),
                    signer,
                    signatureFileVersion,
                    recordFileVersion == 6,
                    recordFileVersion,
                    hapiVersion,
                    blockNumber,
                    startingRunningHash,
                    currentRecordFileWriter.endObjectRunningHash().hash());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================================================================================================================
    // protected methods, used by sub classes and tests

    /**
     * Called on background thread to handle sidecar items. It protobuf serializes them and writes them to the sidecar files. There
     * is only ever one of these running at a time, and they maintain a list of sidecar files that have been written so far but
     * passing it down the chain.
     *
     * @param currentRecordFileFirstTransactionConsensusTimestamp the consensus timestamp of the first transaction in the current record file
     * @param currentSidecarFileWriters the list of sidecars that have been written so far, the last being the latest open one. Can
     *                                  be null if there are no sidecar files yet.
     * @param serializedSingleTransactionRecords List of serialized data from transactions
     * @return the updated list of sidecar files written. Could be the same as passed in or have new ones added.
     */
    protected final List<SidecarFileWriter> handleSidecarItems(
            @NonNull final Instant currentRecordFileFirstTransactionConsensusTimestamp,
            @Nullable final List<SidecarFileWriter> currentSidecarFileWriters,
            @NonNull final List<SerializedSingleTransactionRecord> serializedSingleTransactionRecords) {
        // get or create the latest sidecar file writer
        final List<SidecarFileWriter> sidecarFileWriters =
                currentSidecarFileWriters != null ? new ArrayList<>(currentSidecarFileWriters) : new ArrayList<>();
        SidecarFileWriter latestSidecarWriter =
                sidecarFileWriters.isEmpty() ? null : sidecarFileWriters.get(sidecarFileWriters.size() - 1);
        // iterate over the sidecar records, trying to write each one to sidecar file, creating new sidecar file if
        // needed
        try {
            for (final SerializedSingleTransactionRecord serializedSingleTransactionRecord :
                    serializedSingleTransactionRecords) {
                final int numOfSidecarItems =
                        serializedSingleTransactionRecord.sideCarItemsBytes().size();
                for (int i = 0; i < numOfSidecarItems; i++) {
                    // get the sidecar record
                    final Bytes sidecarRecordBytes = serializedSingleTransactionRecord
                            .sideCarItemsBytes()
                            .get(i);
                    // get the serialized bytes
                    final TransactionSidecarRecord sideCarItem =
                            serializedSingleTransactionRecord.sideCarItems().get(i);

                    // get the kind of the sidecar record
                    final var kind = sideCarItem.sidecarRecords().kind();
                    // check if we have a latestSidecarWriter, if not create one
                    if (latestSidecarWriter == null) {
                        // create a new writer
                        latestSidecarWriter = new SidecarFileWriter(
                                getSidecarFilePath(
                                        currentRecordFileFirstTransactionConsensusTimestamp,
                                        sidecarFileWriters.size() + 1),
                                compressFilesOnCreation,
                                maxSideCarSizeInBytes);
                        // add writer to list
                        sidecarFileWriters.add(latestSidecarWriter);
                    }
                    // try writing to the latest sidecar file writer
                    final boolean wasWritten =
                            latestSidecarWriter.writeTransactionSidecarRecord(kind, sidecarRecordBytes);
                    // if it was not written then the file is full, so create a new one and write to it
                    if (!wasWritten) {
                        // close existing sidecar file writer
                        latestSidecarWriter.close();
                        // create a new writer
                        latestSidecarWriter = new SidecarFileWriter(
                                getSidecarFilePath(
                                        currentRecordFileFirstTransactionConsensusTimestamp,
                                        sidecarFileWriters.size() + 1),
                                compressFilesOnCreation,
                                maxSideCarSizeInBytes);
                        // add writer to list
                        sidecarFileWriters.add(latestSidecarWriter);
                        // now write item to the new writer
                        latestSidecarWriter.writeTransactionSidecarRecord(kind, sidecarRecordBytes);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return sidecarFileWriters;
    }

    /**
     * Get the record file path for a record file with the given consensus time
     *
     * @param consensusTime  a consensus timestamp of the first object to be written in the file
     * @return Path to a record file for that consensus time
     */
    protected Path getRecordFilePath(final Instant consensusTime) {
        return nodeScopedRecordLogDir.resolve(convertInstantToStringWithPadding(consensusTime) + "." + RECORD_EXTENSION
                + (compressFilesOnCreation ? COMPRESSION_ALGORITHM_EXTENSION : ""));
    }

    /**
     * Get full sidecar file path from given Instant object
     *
     * @param currentRecordFileFirstTransactionConsensusTimestamp the consensus timestamp of the first transaction in the
     *                                                            record file this sidecar file is associated with
     * @param sidecarId                                           the sidecar id of this sidecar file
     * @return the new sidecar file path
     */
    protected Path getSidecarFilePath(
            @NonNull final Instant currentRecordFileFirstTransactionConsensusTimestamp, final int sidecarId) {
        return nodeScopedSidecarDir.resolve(
                convertInstantToStringWithPadding(currentRecordFileFirstTransactionConsensusTimestamp)
                        + "_"
                        + String.format("%02d", sidecarId)
                        + "."
                        + RECORD_EXTENSION
                        + (compressFilesOnCreation ? COMPRESSION_ALGORITHM_EXTENSION : ""));
    }
}
