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

package com.hedera.node.app.records.streams.impl.producers;

import com.hedera.hapi.block.stream.BlockHeader;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.MessageDigest;
import java.util.stream.Stream;

/**
 * Defines API for computing running hashes, and converting {@link BlockItem}s into
 * {@link Bytes}. These operations are dependent on the block stream version (v7, etc),
 * but are not otherwise related to the construction of the record file or writing the bytes to a destination such as
 * disk or a socket. Thus, an implementation of this interface for a particular version of the block record format can
 * be reused across the different implementations of {@link BlockStreamWriter}.
 */
public interface BlockStreamFormat {
    /**
     * Serialize the BlockHeader into an intermediary format that can be used for computing running hashes or writing
     * to the file.
     * @param blockHeader The {@link BlockHeader} to serialize.
     * @return the serialized bytes of the block header.
     */
    Bytes serializeBlockHeader(@NonNull final BlockHeader blockHeader);

    /**
     * Serialize a ConsensusEvent into an intermediary format that can be used for computing running hashes or
     * writing to the file.
     *
     * @param consensusEvent The {@link ConsensusEvent} to serialize.
     * @return the serialized bytes of the consensus event.
     */
    @NonNull
    Bytes serializeConsensusEvent(@NonNull final ConsensusEvent consensusEvent);

    /**
     * Serialize a ConsensusTransaction into an intermediary format that can be used for computing running hashes or
     * writing to the file.
     *
     * @param systemTxn The {@link ConsensusTransaction} to serialize.
     * @return the serialized bytes of the system transaction.
     */
    @NonNull
    Bytes serializeSystemTransaction(@NonNull final ConsensusTransaction systemTxn);

    /**
     * Serialize a SingleTransactionRecord into an intermediary format that can be used for computing running hashes or
     * writing to the file. This method returns a Stream of Bytes because the SingleTransactionRecord produces multiple
     * serialized BlockItems.
     *
     * @param item The {@link SingleTransactionRecord} to serialize.
     * @return the serialized bytes of the user transaction.
     */
    @NonNull
    Stream<Bytes> serializeUserTransaction(@NonNull final SingleTransactionRecord item);

    /**
     * Serialize a StateChangeRecorder into an intermediary format that can be used for computing running hashes or
     * writing to the file.
     *
     * @param stateChanges The {@link StateChanges} to serialize.
     * @return the serialized bytes of the state changes.
     */
    @NonNull
    Bytes serializeStateChanges(@NonNull final StateChanges stateChanges);

    /**
     * Serialize a BlockProof into an intermediary format that can be used for writing to the file.
     *
     * @param blockStateProof The {@link BlockProof} to serialize.
     * @return the serialized bytes of the block state proof.
     */
    @NonNull
    Bytes serializeBlockProof(@NonNull final BlockProof blockStateProof);

    /**
     * Returns an instance of a non-thread safe MessageDigest that can be used to compute the hash of block items.
     *
     * @return a non-thread safe MessageDigest instance
     */
    @NonNull
    MessageDigest getMessageDigest();

    /**
     * Given the starting running hash and a stream of serialized {@link BlockItem}s, compute the new running hash by
     * adding each {@link BlockItem}'s hash to the running hash one at a time.
     *
     * @param messageDigest the MessageDigest to use for computing the new running hash. This is an optimization to
     *                      reduce the amount of garbage created when a caller calls this method in a loop.
     * @param startRunningHash the starting current running hash of all record stream items up to this point in time.
     * @param serializedItem the intermediary format serialized {@link BlockItem} to add to the running hash
     * @return the new running hash, or startRunningHash if there were no items to add
     */
    @NonNull
    Bytes computeNewRunningHash(
            @NonNull final MessageDigest messageDigest,
            @NonNull final Bytes startRunningHash,
            @NonNull final Bytes serializedItem);
}
