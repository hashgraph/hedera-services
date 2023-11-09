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

package com.hedera.node.app.records.impl.producers;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.streams.HashAlgorithm;
import com.hedera.hapi.streams.HashObject;
import com.hedera.node.app.annotations.CommonExecutor;
import com.hedera.node.app.records.impl.BlockRecordStreamProducer;
import com.hedera.node.app.spi.info.SelfNodeInfo;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A highly concurrent implementation of {@link BlockRecordStreamProducer}.
 *
 * <p>Threading model: is really important here, This class depends on the fact that public methods are all called on
 * the handle transaction thread, so they are single threaded and executed in the correct order. The information
 * provided by calls to the public methods are then processed into files by the background threads. All async task are
 * done with {@link CompletableFuture}s.
 */
@Singleton
public final class StreamFileProducerConcurrent implements BlockRecordStreamProducer {
    /** Simple pair class */
    record TwoResults<A, B>(A a, B b) {}

    /** The logger */
    private static final Logger logger = LogManager.getLogger(StreamFileProducerConcurrent.class);
    /** Creates new {@link BlockRecordWriter} instances */
    private final BlockRecordWriterFactory writerFactory;
    /** The HAPI protobuf version. Does not change during execution. */
    private final SemanticVersion hapiVersion;
    /** The {@link BlockRecordFormat} used to serialize items for output. */
    private final BlockRecordFormat format;
    /** The executor service to use for background tasks */
    private final ExecutorService executorService;
    /** Future for running hash results of last running hash updates task */
    private CompletableFuture<Bytes> lastRecordHashingResult = null;
    /** Future for running hash results of previous lastRecordHashingResult */
    private CompletableFuture<Bytes> lastRecordHashingResultNMinus1 = null;
    /** Future for running hash results of previous, previous lastRecordHashingResult */
    private CompletableFuture<Bytes> lastRecordHashingResultNMinus2 = null;
    /** Future for running hash results of previous, previous, previous lastRecordHashingResult */
    private CompletableFuture<Bytes> lastRecordHashingResultNMinus3 = null;
    /**
     * Future for the current record file writer, this will always get you the record file writer for the current file
     * being written
     */
    private CompletableFuture<BlockRecordWriter> currentRecordFileWriter = null;
    /** Set in {@link #switchBlocks(long, long, Instant)}, keeps track of the current block number. */
    private long currentBlockNumber;

    /**
     * Construct {@link StreamFileProducerConcurrent}
     *
     * @param nodeInfo the current node information
     * @param format The format to use for the record stream
     * @param writerFactory the factory used to create new {@link BlockRecordWriter} instances
     * @param executorService The executor service to use for background threads
     */
    @Inject
    public StreamFileProducerConcurrent(
            @NonNull final SelfNodeInfo nodeInfo,
            @NonNull final BlockRecordFormat format,
            @NonNull final BlockRecordWriterFactory writerFactory,
            @CommonExecutor @NonNull final ExecutorService executorService) {
        this.writerFactory = requireNonNull(writerFactory);
        this.format = requireNonNull(format);
        hapiVersion = nodeInfo.hapiVersion();
        this.executorService = requireNonNull(executorService);
    }

    // =================================================================================================================
    // Implementation of BlockRecordStreamProducer methods
    //
    // These methods are ALWAYS called on the "handle" thread.

    /** {@inheritDoc} */
    @Override
    public void initRunningHash(@NonNull final RunningHashes runningHashes) {
        if (lastRecordHashingResult != null) {
            throw new IllegalStateException("initRunningHash() can only be called once");
        }

        if (runningHashes.runningHash() == null) {
            throw new IllegalArgumentException("The initial running hash cannot be null");
        }

        lastRecordHashingResult = completedFuture(runningHashes.runningHash());
        lastRecordHashingResultNMinus1 = completedFuture(runningHashes.nMinus1RunningHash());
        lastRecordHashingResultNMinus2 = completedFuture(runningHashes.nMinus2RunningHash());
        lastRecordHashingResultNMinus3 = completedFuture(runningHashes.nMinus3RunningHash());
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Bytes getRunningHash() {
        assert lastRecordHashingResult != null : "initRunningHash() must be called before getRunningHash()";
        return lastRecordHashingResult.join();
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public Bytes getNMinus3RunningHash() {
        assert lastRecordHashingResultNMinus3 != null
                : "initRunningHash() must be called before lastRecordHashingResultNMinus3()";
        return lastRecordHashingResultNMinus3.join();
    }

    /** {@inheritDoc} */
    @Override
    public void switchBlocks(
            final long lastBlockNumber,
            final long newBlockNumber,
            @NonNull final Instant newBlockFirstTransactionConsensusTime) {

        assert lastRecordHashingResult != null : "initRunningHash() must be called before switchBlocks";
        if (newBlockNumber != lastBlockNumber + 1) {
            throw new IllegalArgumentException("Block numbers must be sequential, newBlockNumber=" + newBlockNumber
                    + ", lastBlockNumber=" + lastBlockNumber);
        }

        this.currentBlockNumber = newBlockNumber;
        requireNonNull(newBlockFirstTransactionConsensusTime);

        if (currentRecordFileWriter == null) {
            // We are at the start of a new block and there is no old one to close or wait for. So just create a new one
            // which creates a new file and writes initializes it in the background
            currentRecordFileWriter = lastRecordHashingResult.thenApply(lastRunningHash ->
                    createBlockRecordWriter(lastRunningHash, newBlockFirstTransactionConsensusTime, newBlockNumber));
        } else {
            // wait for all background threads to finish, then in new background task finish the current block
            currentRecordFileWriter = currentRecordFileWriter
                    .thenCombine(lastRecordHashingResult, TwoResults::new)
                    .thenApplyAsync(
                            twoResults -> {
                                final var writer = twoResults.a();
                                final var lastRunningHash = twoResults.b();
                                closeWriter(writer, lastRunningHash);
                                return createBlockRecordWriter(
                                        lastRunningHash, newBlockFirstTransactionConsensusTime, newBlockNumber);
                            },
                            executorService);
        }
    }

    /**
     * Write record items to stream files. They must be in exact consensus time order! This must only be called after the user
     * transaction has been committed to state and is 100% done.
     *
     * @param recordStreamItems the record stream items to write
     */
    @Override
    public void writeRecordStreamItems(@NonNull final Stream<SingleTransactionRecord> recordStreamItems) {
        assert lastRecordHashingResult != null : "initRunningHash() must be called before writeRecordStreamItems";
        assert currentRecordFileWriter != null : "switchBlocks() must be called before writeRecordStreamItems";
        requireNonNull(recordStreamItems);

        // serialize all the record stream items in background thread into SerializedSingleTransaction objects
        final var futureSerializedRecords = CompletableFuture.supplyAsync(
                () -> recordStreamItems
                        .map(item -> format.serialize(item, currentBlockNumber, hapiVersion))
                        .toList(),
                executorService);
        // when serialization is done and previous running hash is computed, we can compute new running hash and write
        // serialized items to record file in parallel update running hash in a background thread
        lastRecordHashingResultNMinus3 = lastRecordHashingResultNMinus2;
        lastRecordHashingResultNMinus2 = lastRecordHashingResultNMinus1;
        lastRecordHashingResultNMinus1 = lastRecordHashingResult;
        lastRecordHashingResult = lastRecordHashingResult
                .thenCombine(futureSerializedRecords, TwoResults::new)
                .thenApplyAsync(
                        twoResults -> format.computeNewRunningHash(twoResults.a(), twoResults.b()), executorService);
        // write serialized items to record file in a background thread
        currentRecordFileWriter = currentRecordFileWriter
                .thenCombine(futureSerializedRecords, TwoResults::new)
                .thenApplyAsync(
                        twoResults -> {
                            final var writer = twoResults.a();
                            final var serializedItems = twoResults.b();
                            serializedItems.forEach(item -> {
                                try {
                                    writer.writeItem(item);
                                } catch (final Exception e) {
                                    logger.error("Error writing record item to file", e);
                                }
                            });
                            return writer;
                        },
                        executorService);
    }

    /**
     * Closes this StreamFileProducerBase wait for any background thread, close all files etc. This method is
     * synchronous, so waits for all background threads to finish and files to be closed.
     */
    @Override
    public void close() {
        if (currentRecordFileWriter != null) {
            CompletableFuture.allOf(currentRecordFileWriter, lastRecordHashingResult)
                    .thenAccept(aVoid -> {
                        final var writer = currentRecordFileWriter.join();
                        final var lastRunningHash = lastRecordHashingResult.join();
                        closeWriter(writer, lastRunningHash);
                    })
                    .join();

            lastRecordHashingResult = null;
            lastRecordHashingResultNMinus1 = null;
            lastRecordHashingResultNMinus2 = null;
            lastRecordHashingResultNMinus3 = null;
            currentRecordFileWriter = null;
        }
    }

    // =================================================================================================================
    // private implementation

    private BlockRecordWriter createBlockRecordWriter(
            @NonNull Bytes lastRunningHash, @NonNull final Instant startConsensusTime, final long blockNumber) {
        try {
            logger.debug("Starting new block record file for block {}", blockNumber);
            final var writer = writerFactory.create();
            final var startRunningHash = asHashObject(lastRunningHash);
            writer.init(hapiVersion, startRunningHash, startConsensusTime, blockNumber);
            return writer;
        } catch (final Exception e) {
            logger.error("Error creating record file writer", e);
            throw e;
        }
    }

    private void closeWriter(BlockRecordWriter writer, Bytes lastRunningHash) {
        // An error here is bad news. But at least, let us catch this error and log it, and
        // move forward with the next block.
        try {
            writer.close(asHashObject(lastRunningHash));
        } catch (final Exception e) {
            logger.error("Error closing record file writer", e);
        }
    }

    private HashObject asHashObject(@NonNull final Bytes hash) {
        return new HashObject(HashAlgorithm.SHA_384, (int) hash.length(), hash);
    }
}
