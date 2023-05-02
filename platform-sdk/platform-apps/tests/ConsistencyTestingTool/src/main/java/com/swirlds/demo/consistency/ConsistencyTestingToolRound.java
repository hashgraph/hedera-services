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

package com.swirlds.demo.consistency;

import static com.swirlds.common.utility.ByteUtils.byteArrayToLong;

import com.swirlds.common.system.Round;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a round in the ConsistencyTestingTool
 *
 * @param roundNumber          The number of the round
 * @param transactionsContents A list of transactions which were included in the round
 */
public record ConsistencyTestingToolRound(long roundNumber, List<Long> transactionsContents)
        implements Comparable<ConsistencyTestingToolRound> {

    /**
     * Construct a {@link ConsistencyTestingToolRound} from a {@link Round}
     *
     * @param round the round to convert
     * @return the input round, converted to a {@link ConsistencyTestingToolRound}
     */
    public static ConsistencyTestingToolRound fromRound(final Round round) {
        final List<Long> transactionContents = new ArrayList<>();

        round.forEachTransaction(transaction -> transactionContents.add(byteArrayToLong(transaction.getContents(), 0)));

        return new ConsistencyTestingToolRound(round.getRoundNum(), transactionContents);
    }

    /**
     * Construct a {@link ConsistencyTestingToolRound} from a string representation
     *
     * @param roundString the string representation of the round
     * @return the new {@link ConsistencyTestingToolRound}
     */
    public static ConsistencyTestingToolRound fromString(final String roundString) {
        final long roundNumber = Long.parseLong(roundString.substring(0, roundString.indexOf(':')));

        final String transactionsString = roundString.substring(roundString.indexOf("[") + 1, roundString.indexOf("]"));
        final List<Long> transactionsContents = Arrays.stream(transactionsString.split("\\s*,\\s*"))
                .map(Long::parseLong)
                .toList();

        return new ConsistencyTestingToolRound(roundNumber, transactionsContents);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(@NonNull ConsistencyTestingToolRound other) {
        return Long.compare(this.roundNumber, other.roundNumber);
    }

    @Override
    public boolean equals(final @Nullable Object other) {
        if (other == null) {
            return false;
        }

        if (other == this) {
            return true;
        }

        if (other instanceof ConsistencyTestingToolRound otherRound) {
            return roundNumber == otherRound.roundNumber
                    && transactionsContents.equals(otherRound.transactionsContents);
        }

        return false;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append("Round Number: ");
        builder.append(roundNumber);
        builder.append("; Transactions: [");

        transactionsContents.forEach(transaction -> {
            builder.append(transaction);
            builder.append(", ");
        });

        builder.append("]\n");

        return builder.toString();
    }
}
