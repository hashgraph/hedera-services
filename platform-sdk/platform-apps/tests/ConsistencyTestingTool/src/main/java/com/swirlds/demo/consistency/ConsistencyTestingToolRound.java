package com.swirlds.demo.consistency;

import static com.swirlds.common.utility.ByteUtils.byteArrayToLong;

import com.swirlds.common.system.Round;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Record representing a round in the ConsistencyTestingTool
 *
 * @param roundNumber          the number of the round
 * @param transactionsContents a list of transactions which were included in the round
 */
public record ConsistencyTestingToolRound(long roundNumber, List<Long> transactionsContents) implements
        Comparable<ConsistencyTestingToolRound> {
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
     * @param roundString the string representation of the round, in the form written by the {@link StateLogWriter}
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
}
