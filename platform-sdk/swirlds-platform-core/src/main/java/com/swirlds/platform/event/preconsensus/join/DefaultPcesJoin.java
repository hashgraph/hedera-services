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

package com.swirlds.platform.event.preconsensus.join;

import com.swirlds.platform.internal.ConsensusRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * A default implementation of {@link PcesJoin}.
 */
public class DefaultPcesJoin implements PcesJoin {

    // TODO metrics why not
    // TODO unit test

    private long durableSequenceNumber = -1;
    private final Queue<ConsensusRound> rounds = new LinkedList<>();

    public DefaultPcesJoin() {}

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<ConsensusRound> setLatestDurableSequenceNumber(final long durableSequenceNumber) {
        if (durableSequenceNumber < this.durableSequenceNumber) {
            throw new IllegalArgumentException(
                    "The durable sequence number cannot be less than the current durable sequence number. "
                            + "Current sequence number: " + this.durableSequenceNumber + ", requested sequence number: "
                            + durableSequenceNumber + ".");
        }
        this.durableSequenceNumber = durableSequenceNumber;

        final List<ConsensusRound> durableRounds = new ArrayList<>();

        while (!rounds.isEmpty() && getKeystoneSequence(rounds.peek()) <= durableSequenceNumber) {
            durableRounds.add(rounds.remove());
        }

        return durableRounds;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<ConsensusRound> addRound(@NonNull final ConsensusRound round) {
        if (round.isEmpty() && getKeystoneSequence(round) <= durableSequenceNumber) {
            return List.of(round);
        }

        rounds.add(round);
        return List.of();
    }

    /**
     * Get the keystone sequence number of the given consensus round.
     *
     * @param consensusRound the consensus round
     * @return the keystone sequence number
     */
    private static long getKeystoneSequence(@NonNull final ConsensusRound consensusRound) {
        return consensusRound.getKeystoneEvent().getBaseEvent().getStreamSequenceNumber();
    }
}
