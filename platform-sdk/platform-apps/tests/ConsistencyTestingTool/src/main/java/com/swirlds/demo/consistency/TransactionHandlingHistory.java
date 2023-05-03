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
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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
     * The location of the log file
     */
    private final Path logFilePath;

    /**
     * Constructor
     * <p>
     * Reads the contents of the log file if it exists, and adds the included rounds to the history
     *
     * @param permitRoundGaps whether gaps in the round history are permitted
     * @param logFilePath     the location of the log file
     */
    public TransactionHandlingHistory(final boolean permitRoundGaps, final @NonNull Path logFilePath) {
        this.permitRoundGaps = permitRoundGaps;
        this.roundHistory = new ArrayList<>();
        this.seenTransactions = new HashSet<>();
        this.logFilePath = logFilePath;

        tryReadLog();
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
        this.logFilePath = that.logFilePath;
    }

    /**
     * Reads the contents of the log file if it exists, and adds the included rounds to the history
     */
    private void tryReadLog() {
        if (!Files.exists(logFilePath)) {
            logger.info(STARTUP.getMarker(), "No log file found. Starting without any previous history");
        }

        logger.info(STARTUP.getMarker(), "Log file found. Parsing previous history");

        try (final BufferedReader reader = new BufferedReader(new FileReader(logFilePath.toFile()))) {
            reader.lines().forEach(line -> addRoundToHistory(ConsistencyTestingToolRound.fromString(line)));
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add a transaction to the list of seen transactions
     *
     * @param transaction the transaction to add
     * @return an error message if the transaction has already been seen, otherwise null
     */
    @Nullable
    private String addTransaction(final long transaction) {
        if (!seenTransactions.add(transaction)) {
            final String error =
                    "Transaction with contents `" + transaction + "` has already been applied to the state";

            logger.error(EXCEPTION.getMarker(), error);
            return error;
        }

        return null;
    }

    /**
     * Searches the history for a round which has the same round number as the new round
     *
     * @param newRound the round to search for a historical counterpart of
     * @return the historical counterpart of the new round, or null if no such round exists
     */
    @Nullable
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
     * @return an error message if the new round doesn't match the historical round, otherwise null
     */
    @Nullable
    private String compareWithHistoricalRound(
            final @NonNull ConsistencyTestingToolRound newRound,
            final @NonNull ConsistencyTestingToolRound historicalRound) {

        if (!newRound.equals(historicalRound)) {
            final String error =
                    "Round " + newRound.roundNumber() + " with transactions " + newRound.transactionsContents()
                            + " doesn't match historical counterpart with transactions "
                            + historicalRound.transactionsContents();

            logger.error(EXCEPTION.getMarker(), error);
            return error;
        }

        return null;
    }

    /**
     * Add a round to the history. Errors are logged.
     *
     * @param newRound the round to add
     * @return a list of errors that occurred while adding the round
     */
    @NonNull
    private List<String> addRoundToHistory(final @NonNull ConsistencyTestingToolRound newRound) {
        final List<String> errors = new ArrayList<>();

        roundHistory.add(newRound);

        newRound.transactionsContents().forEach(transaction -> {
            final String error = addTransaction(transaction);

            if (error != null) {
                errors.add(error);
            }
        });

        if (roundHistory.size() <= 1) {
            // only 1 round is in the history, so no additional checks are necessary
            return errors;
        }

        final long newRoundNumber = newRound.roundNumber();
        final long previousRoundNumber =
                roundHistory.get(roundHistory.size() - 2).roundNumber();

        // make sure round numbers always increase
        if (newRoundNumber <= previousRoundNumber) {
            final String error = "Round " + newRoundNumber + " is not greater than round " + previousRoundNumber;
            logger.error(EXCEPTION.getMarker(), error);

            errors.add(error);
        }
        // if gaps in round history aren't permitted, check that the round numbers are consecutive
        else if (!permitRoundGaps && newRoundNumber != previousRoundNumber + 1) {
            final String error =
                    "Gap in round history found. Round " + newRoundNumber + " was added, but previous round was "
                            + previousRoundNumber;

            logger.error(EXCEPTION.getMarker(), error);

            errors.add(error);
        }

        return errors;
    }

    /**
     * Writes the given round to the log file
     *
     * @param round the round to write to the log file
     */
    private void writeRoundToLog(final @NonNull ConsistencyTestingToolRound round) {
        try (BufferedWriter file = new BufferedWriter(new FileWriter(logFilePath.toFile(), true))) {
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
     * @return a list of errors that occurred while processing the round
     */
    @NonNull
    public List<String> processRound(final @NonNull ConsistencyTestingToolRound round) {
        final ConsistencyTestingToolRound historicalRound = findHistoricalRound(round);

        final List<String> errors = new ArrayList<>();
        if (historicalRound == null) {
            // round doesn't already appear in the history, so record it
            errors.addAll(addRoundToHistory(round));
            writeRoundToLog(round);
        } else {
            // if we found a round with the same round number in the round history, make sure the rounds are identical
            final String error = compareWithHistoricalRound(round, historicalRound);

            if (error != null) {
                errors.add(error);
            }
        }

        return errors;
    }
}
