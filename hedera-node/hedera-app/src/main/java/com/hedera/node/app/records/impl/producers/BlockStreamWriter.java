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

import com.hedera.hapi.streams.HashObject;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.UncheckedIOException;

/**
 *  Writes a block stream to a destination, such as a socket or in memory buffer.
 *
 *  <p>A consensus node takes as input transactions from users, and produces a series of blocks. If the node
 *  fails to produce a block, then the node has effectively failed in its duty to produce a valid blockchain,
 *  and it must halt processing and reconnect. If enough nodes fail to produce a block, then the network will
 *  halt processing transactions. In this way, we can ensure that the network is always making progress without losing
 *  any data in the block stream.
 *
 *  <p>Callers must be prepared to deal with all manner of exceptions. Be prepared to handle Throwable. The callers
 *  <b>can</b> attempt a retry, or they can just fail hard.
 */
public interface BlockStreamWriter {
    /**
     * Initialize the writer to begin accepting a new block of {@link SerializedSingleTransactionRecord}s.
     *
     * <p>>Initialize a new rpc request to a BlockNode. This BlockStreamWriter will not be responsible for deciding
     *     which Block Node to send the request to as that is the job of the gRPC picker.
     *
     * <p>The file implementation of this interface writes directly to a file.
     *
     * @param blockNumber the block number of the block
     * @param previousBlockProofHash the block proof hash of the previous block
     * @throws IllegalStateException if called after {@link #writeItem(Bytes, Bytes)} or after
     *                               {@link #close()}.
     * @throws UncheckedIOException if there is an error writing to the destination
     */
    void init(final long blockNumber, @NonNull final HashObject previousBlockProofHash);

    /**
     * Write a single item to the block stream output. This method may be called multiple times for different items,
     * but must be called after {@link #init(long, HashObject)} and before
     * {@link #close()}.
     *
     * @param item the item to write
     * @param endRunningHash the end running hash after this item is written
     * @throws IllegalStateException if called before {@link #init(long, HashObject)} or after
     *                               {@link #close()}.
     * @throws UncheckedIOException if there is an error writing to the destination
     */
    void writeItem(@NonNull final Bytes item, @NonNull final Bytes endRunningHash);

    /**
     * Close the block that has been produced. Must be called after
     * {@link #init(long, HashObject)}.
     *
     * @throws IllegalStateException if called before {@link #init(long, HashObject)}.
     * @throws UncheckedIOException if there is an error writing to the destination
     */
    void close();
}
