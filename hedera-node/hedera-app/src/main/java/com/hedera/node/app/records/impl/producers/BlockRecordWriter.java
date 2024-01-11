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

package com.hedera.node.app.records.impl.producers;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.streams.HashObject;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.UncheckedIOException;
import java.time.Instant;

/**
 *  Writes a block record to a destination, such as a file or socket or in memory buffer.
 *
 *  <p>A consensus node takes as input transactions from users, and produces a series of block records. If the node
 *  fails to produce a block record, then the node has effectively failed in its duty to produce a valid blockchain,
 *  and it must halt processing and reconnect. If enough nodes fail to produce a block record, then the network will
 *  halt processing transactions. In this way, we can ensure that the network is always making progress without losing
 *  any data in the block record stream.
 *
 *  <p>Callers must be prepared to deal with all manner of exceptions. Be prepared to handle Throwable. The callers
 *  <b>can</b> attempt a retry, or they can just fail hard.
 */
public interface BlockRecordWriter {
    /**
     * Initialize the writer to begin accepting a new block of {@link SerializedSingleTransactionRecord}s.
     *
     * @param hapiProtoVersion the HAPI version of protobuf
     * @param startRunningHash the starting running hash, which is the running hash at the end of previous record file
     * @param startConsensusTime the consensus time of the first <b>user</b> transaction in the block
     * @param blockNumber the block number of the block
     * @throws IllegalStateException if called after {@link #writeItem(SerializedSingleTransactionRecord)} or after
     *                               {@link #close(HashObject)}.
     * @throws UncheckedIOException if there is an error writing to the destination
     */
    void init(
            @NonNull SemanticVersion hapiProtoVersion,
            @NonNull HashObject startRunningHash,
            @NonNull final Instant startConsensusTime,
            final long blockNumber);

    /**
     * Write a single item to the block stream output. This method may be called multiple times for different items,
     * but must be called after {@link #init(SemanticVersion, HashObject, Instant, long)} and before
     * {@link #close(HashObject)}.
     *
     * @param item the item to write
     * @throws IllegalStateException if called before {@link #init(SemanticVersion, HashObject, Instant, long)} or after
     *                               {@link #close(HashObject)}.
     * @throws UncheckedIOException if there is an error writing to the destination
     */
    void writeItem(@NonNull SerializedSingleTransactionRecord item);

    /**
     * Close the block that has been produced. Must be called after
     * {@link #init(SemanticVersion, HashObject, Instant, long)}.
     *
     * @param endRunningHash  the ending running hash after the last record stream item
     * @throws IllegalStateException if called before {@link #init(SemanticVersion, HashObject, Instant, long)}.
     * @throws UncheckedIOException if there is an error writing to the destination
     */
    void close(@NonNull HashObject endRunningHash);
}
