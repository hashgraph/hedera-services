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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Object representing the entire history of transaction handling for the testing app
 * <p>
 * Contains a record of all rounds that have come to consensus, and the transactions which were included
 */
public class TransactionHandlingHistory {
    private static final Logger logger = LogManager.getLogger(TransactionHandlingHistory.class);

    /**
     * The name of the log file being written to / read from
     */
    public static final String LOG_FILE_NAME = "ConsistencyTestLog";

    /**
     * Whether gaps in the round history are permitted. If false, an error will be logged if a gap is found
     */
    private final boolean permitRoundGaps;

    /**
     * A list of rounds that have come to consensus
     */
    private final List<ConsistencyTestingToolRound> roundHistory;

    /**
     * A set of all transactions which have been seen
     */
    private final Set<Long> seenTransactions;

    /**
     * Constructor
     *
     * @param permitRoundGaps whether gaps in the round history are permitted
     */
    public TransactionHandlingHistory(final boolean permitRoundGaps) {
        this.permitRoundGaps = permitRoundGaps;
        this.roundHistory = new ArrayList<>();
        this.seenTransactions = new HashSet<>();
    }

    /**
     * Copy constructor
     *
     * @param that the object to copy
     */
    public TransactionHandlingHistory(@NonNull final TransactionHandlingHistory that) {
        this.permitRoundGaps = that.permitRoundGaps;
        this.roundHistory = new ArrayList<>(that.roundHistory);
        this.seenTransactions = new HashSet<>(that.seenTransactions);
    }

    /**
     * Add a transaction to the list of seen transactions
     *
     * @param transaction the transaction to add
     */
    private void addTransaction(final long transaction) {
        if (!seenTransactions.add(transaction)) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Transaction with contents `{}` has already been applied to the state",
                    transaction);
        }
    }

    /**
     * Searches the history for a round which has the same round number as the new round
     *
     * @param newRound the round to search for a historical counterpart of
     * @return the historical counterpart of the new round, or null if no such round exists
     */
    private ConsistencyTestingToolRound findHistoricalRound(final @NonNull ConsistencyTestingToolRound newRound) {
        return roundHistory.stream()
                .filter(oldRound -> newRound.compareTo(oldRound) == 0)
                .findFirst()
                .orElse(null);
    }

    /**
     * Compare a newly received round with the historical counterpart. Logs an error if the new round isn't identical to
     * the historical round
     *
     * @param newRound        the round that is being newly processed
     * @param historicalRound the historical round that the new round is being compared to
     */
    private void compareWithHistoricalRound(
            final @NonNull ConsistencyTestingToolRound newRound,
            final @NonNull ConsistencyTestingToolRound historicalRound) {

        if (!newRound.equals(historicalRound)) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Round {} with transactions {} doesn't match historical counterpart with transactions {}",
                    newRound.roundNumber(),
                    newRound.transactionsContents(),
                    historicalRound.transactionsContents());
        }
    }

    /**
     * Add a round to the history
     *
     * @param newRound the round to add
     */
    private void addRoundToHistory(final @NonNull ConsistencyTestingToolRound newRound) {
        roundHistory.add(newRound);

        newRound.transactionsContents().forEach(this::addTransaction);

        if (roundHistory.size() <= 1) {
            // only 1 round is in the history, so no additional checks are necessary
            return;
        }

        final long newRoundNumber = newRound.roundNumber();
        final long previousRoundNumber =
                roundHistory.get(roundHistory.size() - 2).roundNumber();

        // make sure round numbers always increase
        if (newRoundNumber <= previousRoundNumber) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Round {} is not greater than round {}",
                    newRoundNumber,
                    previousRoundNumber);
        }
        // if gaps in round history aren't permitted, check that the round numbers are consecutive
        else if (!permitRoundGaps && newRoundNumber != previousRoundNumber + 1) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Gap in round history found. Round {} was added, but previous round was {}",
                    newRoundNumber,
                    previousRoundNumber);
        }
    }

    /**
     * Writes the given round to the log file
     *
     * @param round the round to write to the log file
     */
    private static void writeRoundStateToLog(final @NonNull ConsistencyTestingToolRound round) {
        final Path path = Path.of(getLogFileName());

        try (BufferedWriter file = new BufferedWriter(new FileWriter(path.toFile(), true))) {
            file.write(round.toString());
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Process a round
     * <p>
     * If the input round already exists in the history, this method checks that all transactions are identical to the
     * corresponding historical round
     * <p>
     * If the input round doesn't already exist in the history, this method adds it to the history
     *
     * @param round the round to process
     */
    public void processRound(final @NonNull ConsistencyTestingToolRound round) {
        final ConsistencyTestingToolRound historicalRound = findHistoricalRound(round);

        if (historicalRound == null) {
            // round doesn't already appear in the history, so record it
            addRoundToHistory(round);
            writeRoundStateToLog(round);
        } else {
            // if we found a round with the same round number in the round history, make sure the rounds are identical
            compareWithHistoricalRound(round, historicalRound);
        }
    }

    /**
     * Get the name of the log file being written to / read from during execution of the testing app
     *
     * @return the name of the log file
     */
    public static String getLogFileName() {
        return System.getProperty("user.dir") + File.separator + LOG_FILE_NAME + ".csv";
    }

    /**
     * If a log file exists, parses the log file and adds all rounds to the {@link #roundHistory}
     */
    public void tryParseLog() {
        final Path filePath = Path.of(getLogFileName());

        if (!Files.exists(filePath)) {
            logger.info(STARTUP.getMarker(), "No log file found. Starting without any previous history");
            return;
        }

        logger.info(STARTUP.getMarker(), "Log file found. Parsing previous history");

        try (final BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            processRound(ConsistencyTestingToolRound.fromString(reader.readLine()));
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
