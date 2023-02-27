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

package com.swirlds.platform.consensus;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.state.MinGenInfo;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * A snapshot of consensus at a particular round. This is all the information (except events)
 * consensus needs to continue from a particular point. Apart from this record, consensus needs all
 * non-ancient events to continue.
 *
 * @param round the latest round for which fame has been decided
 * @param judgeHashes the hashes of all the judges for this round, ordered by their creator ID
 * @param minGens the round generation numbers for all non-ancient rounds
 * @param nextConsensusNumber the consensus order of the next event that will reach consensus
 * @param minConsensusTimestamp the minimum consensus timestamp for the next event that reaches
 *     consensus. this is null if no event has reached consensus yet
 */
public record ConsensusSnapshot(
        long round,
        Collection<Hash> judgeHashes,
        List<MinGenInfo> minGens,
        long nextConsensusNumber,
        Instant minConsensusTimestamp) {

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("round: ").append(round).append('\n');
        sb.append("hashes: ").append('\n');
        for (final Hash hash : judgeHashes) {
            sb.append("   ").append(hash.toString()).append('\n');
        }
        sb.append("minGens: ").append('\n');
        minGens.forEach(mg -> sb.append("   ")
                .append(mg.round())
                .append("->")
                .append(mg.minimumGeneration())
                .append('\n'));
        sb.append("nextConsensusNumber: ").append(nextConsensusNumber).append('\n');
        sb.append("minConsensusTimestamp: ").append(minConsensusTimestamp).append('\n');
        return sb.toString();
    }
}
