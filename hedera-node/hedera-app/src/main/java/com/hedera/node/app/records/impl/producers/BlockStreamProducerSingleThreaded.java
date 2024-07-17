/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.hedera.hapi.block.stream.BlockHeader;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.base.BlockHashAlgorithm;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.streams.HashAlgorithm;
import com.hedera.hapi.streams.HashObject;
import com.hedera.node.app.records.impl.BlockStreamProducer;
import com.hedera.node.app.service.addressbook.impl.schemas.V052AddressBookSchema;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.spi.info.SelfNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.MessageDigest;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *  A single threaded implementation of {@link BlockStreamProducer} where operations may block the single calling thread.
 *  This simple implementation is also useful for testing and for the "Hedera Local Node", which is a version of a
 *  consensus node that may be run as a standalone application, useful for development. This class is not thread-safe.
 */
public class BlockStreamProducerSingleThreaded implements BlockStreamProducer {

    private static final Logger logger = LogManager.getLogger(BlockStreamProducerSingleThreaded.class);
    /** Creates new {@link BlockStreamWriter} instances */
    private final BlockStreamWriterFactory writerFactory;
    /** The {@link BlockStreamFormat} used to serialize items for output. */
    private final BlockStreamFormat format;
    /**
     * The {@link BlockStreamWriter} to use for writing produced blocks. A new one is created at the beginning
     * of each new block.
     */
    private volatile BlockStreamWriter writer;
    /** The running hash at end of the last user transaction */
    private Bytes runningHash = null;
    /** The previous running hash */
    private Bytes runningHashNMinus1 = null;
    /** The previous, previous running hash */
    private Bytes runningHashNMinus2 = null;
    /** The previous, previous, previous running hash */
    private Bytes runningHashNMinus3 = null;

    private long currentBlockNumber = 0;

    @Inject
    public BlockStreamProducerSingleThreaded(
            @NonNull final SelfNodeInfo nodeInfo,
            @NonNull final BlockStreamFormat format,
            @NonNull final BlockStreamWriterFactory writerFactory) {
        this.writerFactory = requireNonNull(writerFactory);
        this.format = requireNonNull(format);
    }

    @Override
    public void writeBlockItems(@NonNull Stream<BlockItem> blockItems) {
        blockItems.forEach(blockItem -> {
            final var serializedItem = format.serializeBlockItem(blockItem);
            updateRunningHashes(serializedItem);
            writeSerializedBlockItem(serializedItem);
        });
    }

    private void writeSerializedBlockItem(@NonNull final Bytes serializedItem) {
        try {
            // Depending on the configuration, this writeItem may be an asynchronous or synchronous operation. The
            // BlockStreamWriterFactory instantiated by dagger will determine this.
            writer.writeItem(serializedItem, runningHash);
        } catch (final Exception e) {
            // This **may** prove fatal. The node should be able to carry on, but then fail when it comes to
            // actually producing a valid record stream file. We need to have some way of letting all nodesknow
            // that this node has a problem, so we can make sure at least a minimal threshold of nodes is
            // successfully producing a blockchain.
            logger.error("Error writing block item to block stream writer for block {}", currentBlockNumber, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initFromLastBlock(@NonNull RunningHashes runningHashes, long lastBlockNumber) {
        if (this.runningHash != null) {
            throw new IllegalStateException("initFromLastBlock() must only be called once");
        }

        this.runningHash = runningHashes.runningHash();
        this.runningHashNMinus1 = runningHashes.nMinus1RunningHash();
        this.runningHashNMinus2 = runningHashes.nMinus2RunningHash();
        this.runningHashNMinus3 = runningHashes.nMinus3RunningHash();
        this.currentBlockNumber = lastBlockNumber;
    }

    @NonNull
    @Override
    public Bytes getRunningHash() {
        assert runningHash != null : "initFromLastBlock() must be called before getRunningHash()";
        return runningHash;
    }

    @Nullable
    @Override
    public Bytes getNMinus3RunningHash() {
        return runningHashNMinus3;
    }

    @Override
    public void beginBlock() {
        this.currentBlockNumber++;

        final var lastRunningHash = getRunningHashObject();

        openWriter(this.currentBlockNumber, lastRunningHash);

        // Write the block header to the block stream as the first item in the block.
        writeBlockHeader(lastRunningHash);
    }

    @Override
    public void endBlock() {
        final var lastRunningHash = getRunningHashObject();
        closeWriter(lastRunningHash, this.currentBlockNumber);
    }

    @Override
    public void writeSystemTransaction(ConsensusTransaction platformTxn) {
        final var serializedBlockItem = format.serializeSystemTransaction(platformTxn);
        updateRunningHashes(serializedBlockItem);
        writeSerializedBlockItem(serializedBlockItem);
    }

    @Override
    public void close() throws Exception {
        // FUTURE: close() should wait until the block is completed, which cannot happen until the block proof is
        // produced, and that cannot happen until the signatures have been gossiped. Today, this will close in
        // unpredictable ways, and will most likely result in the block being written incompletely without a block
        // proof.
        final var lastRunningHash = getRunningHashObject();
        closeWriter(lastRunningHash, this.currentBlockNumber);

        runningHash = null;
        runningHashNMinus1 = null;
        runningHashNMinus2 = null;
        runningHashNMinus3 = null;
    }

    private void writeBlockHeader(@NonNull final HashObject previousBlockProofHash) {
        final SemanticVersion hapiProtoVersion = new SemanticVersion(0, 53, 0, null, null);
        V052AddressBookSchema addressBookSchema = new V052AddressBookSchema();
        final var blockHeader = getBlockHeader(previousBlockProofHash, addressBookSchema, hapiProtoVersion);
        final var serializedBlockItem = format.serializeBlockHeader(blockHeader);
        updateRunningHashes(serializedBlockItem);
        writeSerializedBlockItem(serializedBlockItem);
    }

    private @NonNull BlockHeader getBlockHeader(
            @NonNull HashObject previousBlockProofHash,
            V052AddressBookSchema addressBookSchema,
            SemanticVersion hapiProtoVersion) {
        final SemanticVersion addressBookVersion = addressBookSchema.getVersion();
        final long number = this.currentBlockNumber;
        final Bytes previousBlockProofHashBytes = previousBlockProofHash.hash();
        final BlockHashAlgorithm hashAlgorithm = BlockHashAlgorithm.BLOCK_HASH_SHA_384;

        return new BlockHeader(
                hapiProtoVersion, number, previousBlockProofHashBytes, hashAlgorithm, addressBookVersion);
    }

    @NonNull
    private HashObject getRunningHashObject() {
        return asHashObject(getRunningHash());
    }

    @NonNull
    private HashObject asHashObject(@NonNull final Bytes hash) {
        return new HashObject(HashAlgorithm.SHA_384, (int) hash.length(), hash);
    }

    private void closeWriter(@NonNull final HashObject lastRunningHash, final long lastBlockNumber) {
        if (writer != null) {
            // If we fail to close the writer, then this node is almost certainly going to end up in deep trouble.
            // Make sure this is logged. In the FUTURE we may need to do something more drastic, like shut down the
            // node, or maybe retry a number of times before giving up.
            try {
                writer.close();
            } catch (final Exception e) {
                logger.error("Error closing block record writer for block {}", lastBlockNumber, e);
                throw e;
            }
        }
    }

    /**
     * openWriter uses the writerFactory to create a new writer and initialize it for each block. The writer is not
     * re-used.
     * @param newBlockNumber The block number for which the writer is being created.
     * @param previousBlockProofHash The hash of the previous block's proof.
     */
    private void openWriter(final long newBlockNumber, @NonNull final HashObject previousBlockProofHash) {
        try {
            // Depending on the configuration, this writer's methods may be asynchronous or synchronous. The
            // BlockStreamWriterFactory instantiated by dagger will determine this.
            writer = writerFactory.create();
            writer.init(currentBlockNumber, previousBlockProofHash);
        } catch (final Exception e) {
            // This represents an almost certainly fatal error. In the FUTURE we should look at dealing with this in a
            // more comprehensive and consistent way. Maybe we retry a bunch of times before giving up, then restart
            // the node. Or maybe we block forever. Or maybe we disable event intake while we keep trying to get this
            // to work. Or maybe we just shut down the node.
            logger.error("Error creating or initializing a block record writer for block {}", newBlockNumber, e);
            throw e;
        }
    }

    private void updateRunningHashes(@NonNull final Bytes serializedItem) {
        updateRunningHashesWithMessageDigest(format.getMessageDigest(), serializedItem);
    }

    private void updateRunningHashesWithMessageDigest(
            @NonNull final MessageDigest messageDigest, @NonNull final Bytes serializedItem) {
        // Compute new running hash, by adding each serialized BlockItem to the current running hash.
        runningHashNMinus3 = runningHashNMinus2;
        runningHashNMinus2 = runningHashNMinus1;
        runningHashNMinus1 = runningHash;
        runningHash = format.computeNewRunningHash(messageDigest, runningHash, serializedItem);
    }
}
