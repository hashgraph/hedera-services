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

package com.hedera.node.app.blocks;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.swirlds.platform.system.Round;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Maintains the state and process objects needed to produce the block stream.
 * <p>
 * Must receive information about the round boundaries in the consensus algorithm, as it will need to create new hashing
 * objects and advance block metadata at the start of a round. At the end of a round it must commit the updated block
 * metadata to state. In principle, a block can include multiple rounds, although this would require coordination with
 * reconnect to ensure that new nodes always begin with a state on a block boundary.
 * <p>
 * Items written to the stream will be produced in the order they are written. The leaves of the input and output item
 * Merkle trees will be in the order they are written.
 */
public interface BlockStreamManager extends BlockRecordInfo {
    /**
     * Updates the internal state of the block stream manager to reflect the start of a new round.
     * @param round the round that has just started
     * @param state the state of the network at the beginning of the round
     */
    void startRound(@NonNull Round round, @NonNull State state);

    /**
     * Updates both the internal state of the block stream manager and the durable state of the network
     * to reflect the end of the last-started round.
     * @param state the mutable state of the network at the end of the round
     */
    void endRound(@NonNull State state);

    /**
     * Writes a block item to the stream.
     * @param item the block item to write
     * @throws IllegalStateException if the stream is closed
     */
    void writeItem(@NonNull BlockItem item);

    /**
     * Writes the final block items for the freeze round to a new block.
     */
    void writeFreezeRound(@NonNull State state, @NonNull Instant consensusNow);
}