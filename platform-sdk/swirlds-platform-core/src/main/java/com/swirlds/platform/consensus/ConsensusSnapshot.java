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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A snapshot of consensus at a particular round. This is all the information (except events)
 * consensus needs to continue from a particular point. Apart from this record, consensus needs all
 * non-ancient events to continue.
 */
public class ConsensusSnapshot {
    private final long round;
    private final Collection<Hash> judgeHashes;
    private final List<MinGenInfo> minGens;
    private final long nextConsensusNumber;
    private final Instant minConsensusTimestamp;

    /**
     * @param round
     * 		the latest round for which fame has been decided
     * @param judgeHashes
     * 		the hashes of all the judges for this round, ordered by their creator ID
     * @param minGens
     * 		the round generation numbers for all non-ancient rounds
     * @param nextConsensusNumber
     * 		the consensus order of the next event that will reach consensus
     * @param minConsensusTimestamp
     * 		the minimum consensus timestamp for the next event that reaches
     * 		consensus. this is null if no event has reached consensus yet
     */
    public ConsensusSnapshot(
            long round,
            @NonNull Collection<Hash> judgeHashes,
            @NonNull List<MinGenInfo> minGens,
            long nextConsensusNumber,
            // minConsensusTimestamp is null if no event has reached consensus yet, typically in round 1
            @Nullable Instant minConsensusTimestamp) {
        this.round = round;
        this.judgeHashes = judgeHashes;
        this.minGens = minGens;
        this.nextConsensusNumber = nextConsensusNumber;
        this.minConsensusTimestamp = minConsensusTimestamp;
    }

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

    public long round() {
        return round;
    }

    public Collection<Hash> judgeHashes() {
        return judgeHashes;
    }

    public List<MinGenInfo> minGens() {
        return minGens;
    }

    public long nextConsensusNumber() {
        return nextConsensusNumber;
    }

    public Instant minConsensusTimestamp() {
        return minConsensusTimestamp;
    }

    /**
     * Returns the minimum generation below which all events are ancient
     *
     * @param roundsNonAncient
     * 		the number of non-ancient rounds
     * @return minimum non-ancient generation
     */
    public long getMinimumGenerationNonAncient(final int roundsNonAncient) {
        return RoundCalculationUtils.getMinGenNonAncient(roundsNonAncient, round, this::getMinGen);
    }

    /**
     * The minimum generation of famous witnesses for the round specified. This method only looks at non-ancient rounds
     * contained within this state.
     *
     * @param round the round whose minimum generation will be returned
     * @return the minimum generation for the round specified
     * @throws NoSuchElementException if the generation information for this round is not contained withing this state
     */
    public long getMinGen(final long round) {
        for (final MinGenInfo info : minGens()) {
            if (info.round() == round) {
                return info.minimumGeneration();
            }
        }
        for (MinGenInfo minGen : minGens()) {
            System.out.printf("round: %d, minGen: %d\n", minGen.round(), minGen.minimumGeneration());
        }
        throw new NoSuchElementException("No minimum generation found for round: " + round);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ConsensusSnapshot snapshot = (ConsensusSnapshot) o;
        return round == snapshot.round
                && nextConsensusNumber == snapshot.nextConsensusNumber
                && judgeHashes.equals(snapshot.judgeHashes)
                && minGens.equals(snapshot.minGens)
                && Objects.equals(minConsensusTimestamp, snapshot.minConsensusTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(round, judgeHashes, minGens, nextConsensusNumber, minConsensusTimestamp);
    }
}
