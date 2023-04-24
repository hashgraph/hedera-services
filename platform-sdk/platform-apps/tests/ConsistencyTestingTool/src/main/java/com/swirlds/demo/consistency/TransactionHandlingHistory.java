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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Object representing the entire history of transaction handling for the testing app
 * <p>
 * Contains a record of all rounds that have come to consensus, and the transactions which were included
 */
public class TransactionHandlingHistory {
    /**
     * A list of rounds that have come to consensus
     */
    private final List<ConsistencyTestingToolRound> roundHistory;

    /**
     * A set of all transactions which have been seen
     */
    private final Set<Long> seenTransactions = new HashSet<>();

    /**
     * Constructor
     */
    public TransactionHandlingHistory() {
        roundHistory = new ArrayList<>();
    }

    public void addRound(final ConsistencyTestingToolRound round) {
        roundHistory.add(round);
        // TODO add to seen transactions
        // TODO check if this round has already been handled
    }

    /**
     * Parses the log file and adds all rounds to the {@link #roundHistory}
     */
    public void parseLog() {
        final Path filePath = Path.of(ConsistencyTestingToolUtils.getLogFileName());

        try (final BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            addRound(ConsistencyTestingToolRound.fromString(reader.readLine()));
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
