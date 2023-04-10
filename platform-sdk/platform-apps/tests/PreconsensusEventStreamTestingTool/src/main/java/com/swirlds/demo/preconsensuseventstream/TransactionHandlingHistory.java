package com.swirlds.demo.preconsensuseventstream;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Object representing the entire history of transaction handling for the testing app
 * <p>
 * Contains a record of all rounds that have come to consensus, and the transactions which were included
 */
public class TransactionHandlingHistory {
    /**
     * A map of round number to a list of longs, representing the contents of the transactions which came to consensus
     * in the round
     */
    private final SortedMap<Long, List<Long>> roundHistory;

    /**
     * Constructor
     */
    public TransactionHandlingHistory() {
        roundHistory = new TreeMap<>();
    }

    /**
     * Adds a round to the {@link #roundHistory}
     *
     * @param roundString the string read from file representing the round to add
     */
    private void addRound(final @NonNull String roundString) {
        final long roundNumber = Long.parseLong(roundString.substring(0, roundString.indexOf(':')));

        final String transactionsString = roundString.substring(roundString.indexOf("[") + 1, roundString.indexOf("]"));
        final List<Long> transactionsContents = Arrays.stream(transactionsString.split("\\s*,\\s*"))
                .map(Long::parseLong).toList();

        roundHistory.put(roundNumber, transactionsContents);
    }

    /**
     * Parses the log file and adds all rounds to the {@link #roundHistory}
     */
    public void parseLog() {
        final Path filePath = Path.of(PreconsensusEventStreamTestingToolUtils.getLogFileName());

        try (final BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            addRound(reader.readLine());
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
