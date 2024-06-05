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

package com.hedera.node.app.records.streams.impl.producers.formats.v1;

import com.hedera.block.node.api.proto.java.BlockServiceGrpc;
import com.hedera.block.node.api.proto.java.WriteBlockStreamRequest;
import com.hedera.block.node.api.proto.java.WriteBlockStreamResponse;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.streams.HashObject;
import com.hedera.hapi.streams.v7.Block;
import com.hedera.hapi.streams.v7.BlockItem;
import com.hedera.node.app.records.streams.impl.producers.BlockStreamWriter;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.merkle.disk.BlockStreamConfig;
import com.swirlds.state.spi.info.NodeInfo;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.io.UncheckedIOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link BlockStreamWriter} that writes a single {@link Block} to a destination. It writes the
 * block to an output stream. The {@link Block} may be produced and written in pieces of serialized {@link BlockItem}s.
 *
 * <p>All methods are expected to be called on a single thread other than those specified.
 *
 * <p>To supply a block to a block node, this client will need to stream a series of serialized BlockItems representing
 * the complete block.
 *
 * <p>TODO(nickpoorman): Extract retry logic into a "BlockStreamRetryWriter". That writer buffers the entire block until
 *     close is called. This is because the Block Node could disconnect at any time, and this writer will need to
 *     restart uploading again from the beginning of the block.
 */
public final class BlockStreamGrpcWriterV1 implements BlockStreamWriter {
    private static final Logger logger = LogManager.getLogger(BlockStreamGrpcWriterV1.class);

    /** The state of this writer */
    private State state;

    private enum State {
        UNINITIALIZED,
        OPEN,
        CLOSED
    }

    /** WritableStreamingData we are writing to, wraps {@link #bufferedOutputStream} */
    //    private WritableStreamingData outputStream;

    /**
     * A requestObserver for writing a block to the BlockNode.
     */
    private StreamObserver<WriteBlockStreamRequest> requestObserver;

    /**
     * Create a block stream writer to write a block to a block node.
     *
     * @param config The configuration to be used for writing this block. Since this cannot change in
     *     the middle of writing a file, we just need the config, not a config provider.
     * @param nodeInfo The node info for the node writing this file. This is used to get the
     *     node-specific directory where the file will be written.
     */
    public BlockStreamGrpcWriterV1(@NonNull final BlockStreamConfig config, @NonNull final NodeInfo nodeInfo) {

        if (config.blockVersion() != 7) {
            logger.fatal("Bad configuration: BlockStreamWriterV1 used with block version {}", config.blockVersion());
            throw new IllegalArgumentException("Configuration block version is not 7!");
        }

        this.state = State.UNINITIALIZED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final long blockNumber, @NonNull final HashObject startRunningHash) {

        if (state != State.UNINITIALIZED)
            throw new IllegalStateException("Cannot initialize a BlockStreamWriterV1 twice");

        if (blockNumber < 0) throw new IllegalArgumentException("Block number must be non-negative");

        // Get a connection to the block node so that we can start streaming BlockItems to it.
        BlockServiceGrpc.BlockServiceStub client = blockNodeClient();
        StreamObserver<WriteBlockStreamRequest> requestObserver =
                client.writeBlockStream(new StreamObserver<WriteBlockStreamResponse>() {
                    @Override
                    public void onNext(WriteBlockStreamResponse response) {
                        // TODO(nickpoorman): Implement this.
                        logger.info("Response received: " + response.getStatus());
                    }

                    @Override
                    public void onError(Throwable t) {
                        // TODO(nickpoorman): Implement this.
                        t.printStackTrace();
                    }

                    @Override
                    public void onCompleted() {
                        // TODO(nickpoorman): Implement this.
                        logger.info("Stream completed.");
                    }
                });

        // TODO(nickpoorman): For every BlockItem we produce, we will need to send it using:
        //        WriteBlockStreamRequest request = WriteBlockStreamRequest.newBuilder()
        //                // Set request data here
        //                .build();
        //        requestObserver.onNext(request);
        // TODO(nickpoorman): After the final BlockItem is written (i.e. the BlockProof), we will need to call:
        //  requestObserver.onCompleted();
    }

    // =================================================================================================================
    // Implementation of methods in BlockStreamWriter

    /**
     * {@inheritDoc}
     *
     * <p>If we ever bind a bottleneck here, due to the http2 transport having Head-Of-Line blocking, we may want to
     * consider buffering up and batching writes of block items to avoid round trips. This optimization should only be
     * made with extensive benchmarking of various different batch sizes.
     **/
    @Override
    @SuppressWarnings("java:S125")
    public void writeItem(@NonNull final Bytes item, @NonNull final Bytes endRunningHash) {
        if (state != State.OPEN) {
            throw new IllegalStateException("Cannot write to a BlockRecordWriterV6 that is not open");
        }

        // Take the SerializedSingleTransactionRecord and turn it into a BlockItem.
        // TODO(nickpoorman): Implement this.
        //        rec.protobufSerializedRecordStreamItem()

        //        final var itemBytes = rec.protobufSerializedRecordStreamItem();
        //        // [3] - record_stream_items
        //        // FUTURE can change once https://github.com/hashgraph/pbj/issues/44 is fixed to:
        //        // ProtoWriterTools.writeTag(outputStream, RECORD_STREAM_ITEMS,
        //        // ProtoConstants.WIRE_TYPE_DELIMITED);
        //        outputStream.writeVarInt((RECORD_STREAM_ITEMS.number() << TAG_TYPE_BITS) | WIRE_TYPE_DELIMITED,
        // false);
        //        outputStream.writeVarInt((int) itemBytes.length(), false);
        //        outputStream.writeBytes(itemBytes);
        //        handleSidecarItems(rec);
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        if (state != State.OPEN) {
            throw new IllegalStateException("Cannot close a BlockRecordWriterV6 that is not open");
        }

        //        try {
        //            // There are a lot of flushes and closes here, but unfortunately it is not guaranteed that a
        //            // OutputStream
        //            // will propagate though a chain of streams. So we have to flush and close each one
        //            // individually.
        //            bufferedOutputStream.flush();
        //            if (gzipOutputStream != null) gzipOutputStream.flush();
        //            fileOutputStream.flush();
        //
        //            writeFooter(endRunningHash);
        //
        //            outputStream.close();
        //            bufferedOutputStream.close();
        //            if (gzipOutputStream != null) gzipOutputStream.close();
        //            fileOutputStream.close();
        //
        //            closeSidecarFileWriter();
        //
        //            // write signature file, this tells the uploader that this record file set is complete
        //            writeSignatureFile(
        //                    recordFilePath,
        //                    Bytes.wrap(hashingOutputStream.getDigest()),
        //                    signer,
        //                    true,
        //                    6,
        //                    hapiProtoVersion,
        //                    blockNumber,
        //                    startBlockRunningHash.hash(),
        //                    endRunningHash.hash());
        //
        //            this.state = State.CLOSED;
        //        } catch (final IOException e) {
        //            logger.warn("Error closing record file {}", recordFilePath, e);
        //            throw new UncheckedIOException(e);
        //        }
    }

    private void writeHeader(@NonNull final SemanticVersion hapiProtoVersion) throws UncheckedIOException {
        //        try {
        //            // Write the record file version int first to start of file
        //            outputStream.writeInt(VERSION_6);
        //            // [1] - hapi_proto_version
        //            writeMessage(
        //                    outputStream,
        //                    HAPI_PROTO_VERSION,
        //                    hapiProtoVersion,
        //                    SemanticVersion.PROTOBUF::write,
        //                    SemanticVersion.PROTOBUF::measureRecord);
        //            // [2] - start_object_running_hash
        //            writeMessage(
        //                    outputStream,
        //                    START_OBJECT_RUNNING_HASH,
        //                    startBlockRunningHash,
        //                    HashObject.PROTOBUF::write,
        //                    HashObject.PROTOBUF::measureRecord);
        //        } catch (final IOException e) {
        //            logger.warn("Error writing header to record file {}", recordFilePath, e);
        //            throw new UncheckedIOException(e);
        //        }
    }

    // =================================================================================================================
    // Private implementation methods

    /**
     * Write the footer to the file
     *
     * @param endRunningHash the ending running hash after the last record stream item
     */
    private void writeFooter(@NonNull final HashObject endRunningHash) throws UncheckedIOException {
        //        try {
        //            // [4] - end_object_running_hash
        //            writeMessage(
        //                    outputStream,
        //                    END_OBJECT_RUNNING_HASH,
        //                    endRunningHash,
        //                    HashObject.PROTOBUF::write,
        //                    HashObject.PROTOBUF::measureRecord);
        //            // [5] - block_number
        //            writeLong(outputStream, BLOCK_NUMBER, blockNumber);
        //            // [6] - sidecars
        //            ProtoWriterTools.writeMessageList(
        //                    outputStream,
        //                    SIDECARS,
        //                    sidecarMetadata == null ? Collections.emptyList() : sidecarMetadata,
        //                    SidecarMetadata.PROTOBUF::write,
        //                    SidecarMetadata.PROTOBUF::measureRecord);
        //        } catch (IOException e) {
        //            logger.warn("Error writing footer to record file {}", recordFilePath, e);
        //            throw new UncheckedIOException(e);
        //        }
    }

    //    /**
    //     * @param rec Current item to write to the sidecar(s)
    //     */
    //    private void handleSidecarItems(@NonNull final SerializedSingleTransactionRecord rec) {
    //        try {
    //            final var sideCarItemsBytesList = rec.sideCarItemsBytes();
    //            final int numOfSidecarItems = sideCarItemsBytesList.size();
    //            for (int i = 0; i < numOfSidecarItems; i++) {
    //                // get the sidecar record
    //                final var sidecarRecordBytes = sideCarItemsBytesList.get(i);
    //                // get the serialized bytes
    //                final var sideCarItem = rec.sideCarItems().get(i);
    //                // get the kind of the sidecar record
    //                final var kind = sideCarItem.sidecarRecords().kind();
    //                // check if we have a latestSidecarWriter, if not create one
    //                if (sidecarFileWriter == null) sidecarFileWriter = createSidecarFileWriter(1);
    //                // try writing to the sidecar file
    //                final boolean wasWritten = sidecarFileWriter.writeTransactionSidecarRecord(kind,
    // sidecarRecordBytes);
    //                // if it was not written then the file is full, so create a new one and write to it
    //                if (!wasWritten) {
    //                    // close existing sidecar file writer
    //                    closeSidecarFileWriter();
    //
    //                    // create a new writer
    //                    sidecarFileWriter = createSidecarFileWriter(sidecarFileWriter.id() + 1);
    //
    //                    // now write item to the new writer
    //                    if (!sidecarFileWriter.writeTransactionSidecarRecord(kind, sidecarRecordBytes)) {
    //                        // If it failed to write a second time, then the sidecar file is too small to hold this
    //                        // item.
    //                        // However, since sidecars are not mandatory, we can just log a warning and move on.
    //                        logger.warn(
    //                                "Sidecar file is too large and cannot be written. Sidecar size: {} bytes",
    //                                sidecarRecordBytes.length());
    //                    }
    //                }
    //            }
    //        } catch (final IOException e) {
    //            // NOTE: Writing sidecar files really is best-effort, if it doesn't happen, we're OK with just
    //            // logging the
    //            // warning and moving on.
    //            logger.warn("Error writing sidecar file", e);
    //        }
    //    }
    //
    //    @NonNull
    //    private SidecarWriterV6 createSidecarFileWriter(final int id) throws IOException {
    //        return new SidecarWriterV6(getSidecarFilePath(id), compressFiles, maxSideCarSizeInBytes, id);
    //    }
    //
    //    private void closeSidecarFileWriter() {
    //        try {
    //            if (sidecarFileWriter != null) {
    //                // close existing sidecar file writer
    //                sidecarFileWriter.close();
    //                // get the sidecar hash
    //                final Bytes sidecarHash = sidecarFileWriter.fileHash();
    //                // create and add sidecar metadata to record file
    //                if (sidecarMetadata == null) sidecarMetadata = new ArrayList<>();
    //                sidecarMetadata.add(new SidecarMetadata(
    //                        new HashObject(HashAlgorithm.SHA_384, (int) sidecarHash.length(), sidecarHash),
    //                        sidecarFileWriter.id(),
    //                        sidecarFileWriter.types()));
    //            }
    //        } catch (final IOException e) {
    //            // NOTE: Writing sidecar files really is best-effort, if it doesn't happen, we're OK with just
    //            // logging the
    //            // warning and moving on.
    //            logger.warn("Error closing sidecar file", e);
    //        }
    //    }
    //
    //    /**
    //     * Get the record file path for a record file with the given consensus time
    //     *
    //     * @param consensusTime a consensus timestamp of the first object to be written in the file
    //     * @return Path to a record file for that consensus time
    //     */
    //    @NonNull
    //    private Path getRecordFilePath(final Instant consensusTime) {
    //        return nodeScopedRecordDir.resolve(convertInstantToStringWithPadding(consensusTime)
    //                + "."
    //                + RECORD_EXTENSION
    //                + (compressFiles ? COMPRESSION_ALGORITHM_EXTENSION : ""));
    //    }
    //
    //    /**
    //     * Get full sidecar file path from given Instant object
    //     *
    //     * @param sidecarId the sidecar id of this sidecar file
    //     * @return the new sidecar file path
    //     */
    //    @NonNull
    //    private Path getSidecarFilePath(final int sidecarId) {
    //        return nodeScopedSidecarDir.resolve(convertInstantToStringWithPadding(startConsensusTime)
    //                + "_"
    //                + String.format("%02d", sidecarId)
    //                + "."
    //                + RECORD_EXTENSION
    //                + (compressFiles ? COMPRESSION_ALGORITHM_EXTENSION : ""));
    //    }
    //
    //    /**
    //     * Create the digest for hashing the file contents. The record file V6 format requires the hash of
    //     * ALL the bytes of the file. These bytes are then used to sign the file and submit as a signature
    //     * file. This is distinct from the running hash which is the hash of the bytes of the record item.
    //     *
    //     * @return a new message digest
    //     * @throws RuntimeException if the digest algorithm is not found
    //     */
    //    @NonNull
    //    private MessageDigest createWholeFileMessageDigest() {
    //        try {
    //            return MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
    //        } catch (NoSuchAlgorithmException e) {
    //            // If we are unable to create the message digest, then we have a serious problem. This should
    //            // never be
    //            // possible because the node should never have been able to start up in this case. But should
    //            // it ever
    //            // happen, it would mean that the node was unable to create a new record file writer, and it
    //            // needs to stop
    //            // processing new transactions.
    //            logger.fatal("Unable to create message digest", e);
    //            throw new RuntimeException(e);
    //        }
    //    }

    @NonNull
    private BlockServiceGrpc.BlockServiceStub blockNodeClient() {
        // TODO(nickpoorman): Implement this. These details should be provided in the config.
        //   We should also reuse the ManagedChannel and stub and not create a new one for each rpc call.
        //   That means we probably want to hoist this out into a factory that provides it via dagger.
        var host = "localhost";
        var port = 50211;
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        return BlockServiceGrpc.newStub(channel);
    }
}
